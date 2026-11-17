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
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** BLK-RPT-001 패치 신고. 숨기고 같은 담요 칸을 AI 기본 메시지로 바꾼다. */
@Slf4j
@Service
public class CheerReportService {

  static final int RESTRICTION_REPORT_COUNT = 3;
  static final int RESTRICTION_WINDOW_DAYS = 30;
  static final int RESTRICTION_HOURS = 24;

  private final CheerRepository cheerRepository;
  private final CheerReportRepository cheerReportRepository;
  private final SendRestrictionRepository sendRestrictionRepository;
  private final BlanketPieceRepository blanketPieceRepository;
  private final UserRepository userRepository;
  private final DefaultCheerMessageGenerator defaultMessageGenerator;
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
      DefaultCheerMessageGenerator defaultMessageGenerator,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.cheerRepository = cheerRepository;
    this.cheerReportRepository = cheerReportRepository;
    this.sendRestrictionRepository = sendRestrictionRepository;
    this.blanketPieceRepository = blanketPieceRepository;
    this.userRepository = userRepository;
    this.defaultMessageGenerator = defaultMessageGenerator;
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
    if (!blanketPieceRepository.existsByCheerIdAndBlanketUserId(cheerId, userId)) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    Emotion emotion = reported.getTargetEmotion();
    Set<String> receivedDefaults = blanketPieceRepository.findReceivedDefaultMessages(userId);
    String defaultMessage = defaultMessageGenerator.generate(emotion, receivedDefaults);

    ReceivedCheerResponse replaced =
        transactionTemplate.execute(
            status -> hideAndReplace(userId, cheerId, emotion, defaultMessage));
    return new CheerReportResponse(replaced);
  }

  private ReceivedCheerResponse hideAndReplace(
      Long userId, Long cheerId, Emotion emotion, String defaultMessage) {
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

    piece.replace(null, defaultMessage);

    return ReceivedCheerResponse.ofDefault(emotion, defaultMessage);
  }

  /** BLK-RPT-002 보내기 제한. 발신자 행을 잠근 뒤 최근 30일(마지막 해제 시각 이후) 신고가 3번이면 24시간 제한한다. 이미 제한 중이면 늘리지 않는다. */
  private void restrictSenderIfNeeded(Long cheerId, String reportedHash, LocalDateTime now) {
    sendRestrictionRepository.insertOrLock(reportedHash, now);
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
