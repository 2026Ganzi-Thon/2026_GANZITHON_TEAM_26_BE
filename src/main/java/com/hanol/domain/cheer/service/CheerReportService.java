package com.hanol.domain.cheer.service;

import com.hanol.domain.blanket.entity.BlanketPiece;
import com.hanol.domain.blanket.repository.BlanketPieceRepository;
import com.hanol.domain.cheer.dto.response.CheerReportResponse;
import com.hanol.domain.cheer.dto.response.ReceivedCheerResponse;
import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.cheer.entity.CheerReport;
import com.hanol.domain.cheer.entity.CheerStatus;
import com.hanol.domain.cheer.repository.CheerReportRepository;
import com.hanol.domain.cheer.repository.CheerRepository;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.mood.repository.MoodRecordCardRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** BLK-RPT-001 패치 신고. 숨기고 같은 담요 칸을 새 패치(또는 기본 메시지)로 바꾼다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheerReportService {

  static final int CLAIM_CANDIDATE_SIZE = 5;

  private final CheerRepository cheerRepository;
  private final CheerReportRepository cheerReportRepository;
  private final BlanketPieceRepository blanketPieceRepository;
  private final MoodRecordCardRepository moodRecordCardRepository;
  private final UserRepository userRepository;
  private final CheerSenderHasher cheerSenderHasher;
  private final DefaultCheerMessageGenerator defaultMessageGenerator;
  private final CheerFallbackMessages fallbackMessages;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;

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
    moodRecordCardRepository
        .findFirstByBlanketPieceId(piece.getId())
        .ifPresent(card -> card.replaceReceivedMessage(message));

    return replacement != null
        ? ReceivedCheerResponse.ofCheer(replacement.getId(), emotion, message)
        : ReceivedCheerResponse.ofDefault(emotion, message);
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
