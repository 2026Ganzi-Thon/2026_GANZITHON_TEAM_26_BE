package com.hanol.domain.cheer.service;

import com.hanol.domain.cheer.dto.request.CheerSendRequest;
import com.hanol.domain.cheer.dto.response.CheerAvailabilityResponse;
import com.hanol.domain.cheer.dto.response.CheerSendResponse;
import com.hanol.domain.cheer.dto.response.EmotionQuestionGroup;
import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.cheer.entity.CheerStatus;
import com.hanol.domain.cheer.repository.CheerRepository;
import com.hanol.domain.cheer.repository.CheerRepository.EmotionCount;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.user.entity.DailyStat;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.DailyStatRepository;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheerService {

  private final CheerRepository cheerRepository;
  private final CheerReviewer cheerReviewer;
  private final CheerSenderHasher cheerSenderHasher;
  private final UserRepository userRepository;
  private final DailyStatRepository dailyStatRepository;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;

  /** BLK-SEND-001 보내기 가능 확인. 대기 중인 패치가 적은 감정부터, 같으면 Emotion 선언 순서. */
  @Transactional(readOnly = true)
  public CheerAvailabilityResponse getAvailability() {
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

    // 보내기 제한(BLK-RPT-002, P1)이 생기기 전까지는 항상 보낼 수 있다.
    return new CheerAvailabilityResponse(true, questions);
  }

  /** BLK-SEND-002 패치 검사·발송. 검수(AI)는 트랜잭션 밖에서 한다. */
  public CheerSendResponse send(Long userId, CheerSendRequest request) {
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
}
