package com.hanol.domain.cheer.service;

import com.hanol.domain.blanket.entity.BlanketPiece;
import com.hanol.domain.blanket.repository.BlanketPieceRepository;
import com.hanol.domain.cheer.dto.response.CheerReportResponse;
import com.hanol.domain.cheer.dto.response.ReceivedCheerResponse;
import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.cheer.entity.CheerReport;
import com.hanol.domain.cheer.entity.CheerStatus;
import com.hanol.domain.cheer.entity.SendRestriction;
import com.hanol.domain.cheer.repository.CheerReportRepository;
import com.hanol.domain.cheer.repository.CheerRepository;
import com.hanol.domain.cheer.repository.SendRestrictionRepository;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** BLK-RPT-001 패치 신고. 숨기고 같은 담요 칸을 새 패치(또는 기본 메시지)로 바꾼다. */
@Slf4j
@Service
public class CheerReportService {

  static final int CLAIM_CANDIDATE_SIZE = 5;
  static final int RESTRICTION_REPORT_COUNT = 3;
  static final int RESTRICTION_WINDOW_DAYS = 30;
  static final int RESTRICTION_HOURS = 24;

  private final CheerRepository cheerRepository;
  private final CheerReportRepository cheerReportRepository;
  private final SendRestrictionRepository sendRestrictionRepository;
  private final BlanketPieceRepository blanketPieceRepository;
  private final UserRepository userRepository;
  private final CheerSenderHasher cheerSenderHasher;
  private final DefaultCheerMessageGenerator defaultMessageGenerator;
  private final CheerFallbackMessages fallbackMessages;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;

  /**
   * 신고 트랜잭션은 READ_COMMITTED 로 돈다. REPEATABLE READ 에서는 send_restrictions 행 잠금을 기다린 뒤에도 첫 조회 시점 스냅샷으로
   * 신고 수를 세서, 먼저 커밋된 다른 신고를 놓쳐 제한이 누락된다.
   */
  public CheerReportService(
      CheerRepository cheerRepository,
      CheerReportRepository cheerReportRepository,
      SendRestrictionRepository sendRestrictionRepository,
      BlanketPieceRepository blanketPieceRepository,
      UserRepository userRepository,
      CheerSenderHasher cheerSenderHasher,
      DefaultCheerMessageGenerator defaultMessageGenerator,
      CheerFallbackMessages fallbackMessages,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.cheerRepository = cheerRepository;
    this.cheerReportRepository = cheerReportRepository;
    this.sendRestrictionRepository = sendRestrictionRepository;
    this.blanketPieceRepository = blanketPieceRepository;
    this.userRepository = userRepository;
    this.cheerSenderHasher = cheerSenderHasher;
    this.defaultMessageGenerator = defaultMessageGenerator;
    this.fallbackMessages = fallbackMessages;
    this.clock = clock;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public CheerReportResponse report(Long userId, Long cheerId) {
    Cheer reported =
        cheerRepository
            .findById(cheerId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    validateNotHidden(reported);
    findMyPiece(userId, cheerId);

    Emotion emotion = reported.getTargetEmotion();
    String senderHash = cheerSenderHasher.hash(userId);
    Set<String> receivedDefaults =
        blanketPieceRepository.findDefaultContents(userId).stream()
            .map(String::strip)
            .collect(Collectors.toSet());
    String defaultMessage =
        cheerRepository.existsByTargetEmotionAndStatusAndSenderHashNot(
                emotion, CheerStatus.WAITING, senderHash)
            ? null
            : defaultMessageGenerator.generate(emotion, receivedDefaults);

    ReceivedCheerResponse replaced =
        transactionTemplate.execute(
            status ->
                hideAndReplace(
                    userId, cheerId, emotion, senderHash, defaultMessage, receivedDefaults));
    return new CheerReportResponse(replaced);
  }

  private ReceivedCheerResponse hideAndReplace(
      Long userId,
      Long cheerId,
      Emotion emotion,
      String senderHash,
      String defaultMessage,
      Set<String> receivedDefaults) {
    userRepository
        .findByIdForUpdate(userId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    Cheer reported =
        cheerRepository
            .findById(cheerId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    validateNotHidden(reported);
    final BlanketPiece piece = findMyPiece(userId, cheerId);
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);

    if (cheerRepository.changeStatusIf(cheerId, CheerStatus.DELIVERED, CheerStatus.HIDDEN, now)
        != 1) {
      throw new BusinessException(CommonErrorCode.CONFLICT);
    }
    cheerReportRepository.save(
        CheerReport.builder()
            .cheer(reported)
            .senderHash(reported.getSenderHash())
            .reportedAt(now)
            .build());
    log.info("Cheer reported. cheerId={}", cheerId);
    restrictSenderIfNeeded(cheerId, reported.getSenderHash(), now);

    Cheer replacement =
        defaultMessage == null ? claim(emotion, senderHash, now).orElse(null) : null;
    String message;
    if (replacement != null) {
      message = replacement.getContent();
    } else if (defaultMessage != null) {
      message = defaultMessage;
    } else {
      log.info("Cheer claim lost, using fallback. emotion={}", emotion);
      message = fallbackMessages.pick(emotion, receivedDefaults);
    }

    piece.replace(replacement, message);

    return replacement != null
        ? ReceivedCheerResponse.ofCheer(replacement.getId(), emotion, message)
        : ReceivedCheerResponse.ofDefault(emotion, message);
  }

  /** BLK-RPT-002 보내기 제한. 발신자 행을 잠근 뒤 최근 30일(마지막 해제 시각 이후) 신고가 3번이면 24시간 제한한다. 이미 제한 중이면 늘리지 않는다. */
  private void restrictSenderIfNeeded(Long cheerId, String reportedHash, LocalDateTime now) {
    sendRestrictionRepository.insertIfAbsent(reportedHash, now);
    SendRestriction restriction =
        sendRestrictionRepository.findBySenderHashForUpdate(reportedHash).orElseThrow();
    if (restriction.isRestrictedAt(now)) {
      return;
    }
    LocalDateTime since = now.minusDays(RESTRICTION_WINDOW_DAYS);
    LocalDateTime lastRestrictedUntil = restriction.getRestrictedUntil();
    if (lastRestrictedUntil != null && lastRestrictedUntil.isAfter(since)) {
      since = lastRestrictedUntil;
    }
    if (cheerReportRepository.countBySenderHashAndReportedAtAfter(reportedHash, since)
        >= RESTRICTION_REPORT_COUNT) {
      restriction.restrictUntil(now.plusHours(RESTRICTION_HOURS));
      log.info(
          "Cheer sender restricted. cheerId={}, restrictedUntil={}",
          cheerId,
          restriction.getRestrictedUntil());
    }
  }

  /** 후보를 오래된 순으로 보며 WAITING → DELIVERED 선점. 1행이 바뀐 것만 내 것이다. */
  private Optional<Cheer> claim(Emotion emotion, String senderHash, LocalDateTime now) {
    List<Cheer> candidates =
        cheerRepository.findQueueCandidates(
            emotion, CheerStatus.WAITING, senderHash, PageRequest.of(0, CLAIM_CANDIDATE_SIZE));
    for (Cheer candidate : candidates) {
      if (cheerRepository.changeStatusIf(
              candidate.getId(), CheerStatus.WAITING, CheerStatus.DELIVERED, now)
          == 1) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  private BlanketPiece findMyPiece(Long userId, Long cheerId) {
    return blanketPieceRepository
        .findFirstByCheerIdAndBlanketUserId(cheerId, userId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN));
  }

  private static void validateNotHidden(Cheer cheer) {
    if (cheer.getStatus() == CheerStatus.HIDDEN) {
      throw new BusinessException(CommonErrorCode.CONFLICT);
    }
  }
}
