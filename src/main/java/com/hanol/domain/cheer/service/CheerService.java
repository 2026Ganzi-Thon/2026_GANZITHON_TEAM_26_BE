package com.hanol.domain.cheer.service;

import com.hanol.domain.cheer.dto.request.CheerSendRequest;
import com.hanol.domain.cheer.dto.response.CheerAvailabilityResponse;
import com.hanol.domain.cheer.dto.response.CheerSendResponse;
import com.hanol.domain.cheer.dto.response.EmotionQuestionGroup;
import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.cheer.entity.CheerStatus;
import com.hanol.domain.cheer.entity.SendRestriction;
import com.hanol.domain.cheer.exception.CheerErrorCode;
import com.hanol.domain.cheer.repository.CheerRepository;
import com.hanol.domain.cheer.repository.CheerRepository.EmotionCount;
import com.hanol.domain.cheer.repository.SendRestrictionRepository;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.user.entity.DailyStat;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.DailyStatRepository;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheerService {

  static final Duration SEND_COOLDOWN = Duration.ofMinutes(5);

  private final CheerRepository cheerRepository;
  private final CheerReviewer cheerReviewer;
  private final CheerSenderHasher cheerSenderHasher;
  private final SendRestrictionRepository sendRestrictionRepository;
  private final UserRepository userRepository;
  private final DailyStatRepository dailyStatRepository;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;

  /** BLK-SEND-001 보내기 가능 확인. 대기 중인 패치가 적은 감정부터, 같으면 Emotion 선언 순서. */
  @Transactional(readOnly = true)
  public CheerAvailabilityResponse getAvailability(Long userId) {
    LocalDateTime now = LocalDateTime.now(clock);
    Optional<LocalDateTime> restrictedUntil =
        findActiveRestriction(userId).map(SendRestriction::getRestrictedUntil);
    Optional<LocalDateTime> cooldownUntil = cooldownUntil(userId, now);
    if (restrictedUntil.isPresent() || cooldownUntil.isPresent()) {
      return new CheerAvailabilityResponse(
          false, restrictedUntil.orElse(null), cooldownUntil.orElse(null), List.of());
    }

    Map<Emotion, Long> waitingCounts = new EnumMap<>(Emotion.class);
    for (EmotionCount row : cheerRepository.countGroupByTargetEmotion(CheerStatus.WAITING)) {
      waitingCounts.put(row.getEmotion(), row.getCount());
    }

    List<EmotionQuestionGroup> questions =
        Arrays.stream(Emotion.values())
            .sorted(
                Comparator.comparingLong((Emotion e) -> waitingCounts.getOrDefault(e, 0L))
                    .thenComparing(Comparator.naturalOrder()))
            .map(EmotionQuestionGroup::from)
            .toList();

    return new CheerAvailabilityResponse(true, null, null, questions);
  }

  /** BLK-SEND-002 패치 검사·발송. 검수(AI)는 트랜잭션 밖에서 한다. */
  public CheerSendResponse send(Long userId, CheerSendRequest request) {
    validateNotRestricted(userId);
    validateNotCoolingDown(userId);
    CheerReviewResult result = cheerReviewer.review(request.content());
    if (result == CheerReviewResult.CRISIS) {
      log.info("Crisis expression detected in cheer. userId={}", userId);
      return new CheerSendResponse(true);
    }
    if (result.isRejected()) {
      log.info("Cheer rejected. result={}", result);
      throw new BusinessException(result.getErrorCode());
    }

    transactionTemplate.execute(status -> saveCheer(userId, request));
    return new CheerSendResponse(false);
  }

  private Cheer saveCheer(Long userId, CheerSendRequest request) {
    User user =
        userRepository
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    validateNotRestricted(userId);
    validateNotCoolingDown(userId);
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);

    DailyStat dailyStat =
        dailyStatRepository
            .findByUserIdAndStatDate(userId, now.toLocalDate())
            .orElseGet(
                () ->
                    dailyStatRepository.save(
                        DailyStat.builder().user(user).statDate(now.toLocalDate()).build()));
    dailyStat.increaseSentCount(now);

    Cheer cheer =
        cheerRepository.save(
            Cheer.builder()
                .senderHash(cheerSenderHasher.hash(userId))
                .targetEmotion(request.targetEmotion())
                .content(request.content())
                .build());
    log.info("Cheer saved. cheerId={}", cheer.getId());
    return cheer;
  }

  /** BLK-RPT-002 보내기 제한 중이면 403. */
  private void validateNotRestricted(Long userId) {
    if (findActiveRestriction(userId).isPresent()) {
      throw new BusinessException(CheerErrorCode.CHEER_SEND_RESTRICTED);
    }
  }

  /** 마지막 발송 성공 후 5분이 지나지 않았으면 429. */
  private void validateNotCoolingDown(Long userId) {
    if (cooldownUntil(userId, LocalDateTime.now(clock)).isPresent()) {
      throw new BusinessException(CheerErrorCode.CHEER_COOLDOWN);
    }
  }

  /** 날짜와 상관없이 가장 최근 발송 성공 시각 + 5분. 쿨타임이 끝났으면 empty. */
  private Optional<LocalDateTime> cooldownUntil(Long userId, LocalDateTime now) {
    return dailyStatRepository
        .findLastSentAt(userId)
        .map(t -> t.plus(SEND_COOLDOWN))
        .filter(until -> until.isAfter(now));
  }

  private Optional<SendRestriction> findActiveRestriction(Long userId) {
    return sendRestrictionRepository
        .findBySenderHash(cheerSenderHasher.hash(userId))
        .filter(r -> r.isRestrictedAt(LocalDateTime.now(clock)));
  }
}
