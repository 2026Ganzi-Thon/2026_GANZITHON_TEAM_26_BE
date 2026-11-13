package com.hanol.domain.mood.service;

import com.hanol.domain.alert.service.CrisisDetector;
import com.hanol.domain.interaction.entity.WarmthMapLog;
import com.hanol.domain.interaction.repository.WarmthMapLogRepository;
import com.hanol.domain.mood.dto.request.MoodRecordCreateRequest;
import com.hanol.domain.mood.dto.request.StoryCheckRequest;
import com.hanol.domain.mood.dto.response.MoodRecordCreateResponse;
import com.hanol.domain.mood.dto.response.StoryCheckResponse;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.mood.entity.MoodRecord;
import com.hanol.domain.mood.entity.MoodRecordCard;
import com.hanol.domain.mood.entity.Story;
import com.hanol.domain.mood.repository.MoodRecordCardRepository;
import com.hanol.domain.mood.repository.MoodRecordRepository;
import com.hanol.domain.mood.repository.StoryRepository;
import com.hanol.domain.user.entity.DailyStat;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.DailyStatRepository;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MoodService {

  private static final long WARMTH_MAP_DEDUP_MINUTES = 30;

  private final CrisisDetector crisisDetector;
  private final UserRepository userRepository;
  private final DailyStatRepository dailyStatRepository;
  private final MoodRecordRepository moodRecordRepository;
  private final MoodRecordCardRepository moodRecordCardRepository;
  private final StoryRepository storyRepository;
  private final WarmthMapLogRepository warmthMapLogRepository;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;

  public StoryCheckResponse checkStory(StoryCheckRequest request) {
    return new StoryCheckResponse(crisisDetector.isCrisis(request.content()));
  }

  public MoodRecordCreateResponse createRecord(Long userId, MoodRecordCreateRequest request) {
    List<Emotion> emotions = request.emotions();
    if (new HashSet<>(emotions).size() != emotions.size()) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    String story = request.story();
    boolean hasStory = story != null && !story.isBlank();
    if (hasStory) {
      try {
        if (crisisDetector.isCrisis(story)) {
          log.info("Crisis expression detected in mood record story. userId={}", userId);
        }
      } catch (BusinessException exception) {
        log.warn("Crisis re-check failed, saving record anyway. userId={}", userId);
      }
    }

    return transactionTemplate.execute(status -> saveRecord(userId, emotions, story, hasStory));
  }

  private MoodRecordCreateResponse saveRecord(
      Long userId, List<Emotion> emotions, String story, boolean hasStory) {
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
    dailyStat.increasePickCount();
    int todayCount = dailyStat.getPickCount();

    MoodRecord record =
        moodRecordRepository.save(
            MoodRecord.builder().user(user).recordedAt(now).dailySeq(todayCount).build());
    for (int i = 0; i < emotions.size(); i++) {
      moodRecordCardRepository.save(
          MoodRecordCard.builder()
              .moodRecord(record)
              .cardOrder(i + 1)
              .emotion(emotions.get(i))
              .build());
    }

    if (hasStory) {
      storyRepository.save(Story.builder().moodRecord(record).content(story).build());
    }

    LocalDateTime dedupFrom = now.minusMinutes(WARMTH_MAP_DEDUP_MINUTES);
    for (Emotion emotion : emotions) {
      if (!warmthMapLogRepository.existsByUserIdAndEmotionAndLoggedAtAfter(
          userId, emotion, dedupFrom)) {
        warmthMapLogRepository.save(
            WarmthMapLog.builder().user(user).emotion(emotion).loggedAt(now).build());
      }
    }

    return new MoodRecordCreateResponse(
        record.getId(), emotions.stream().map(Emotion::name).toList(), now, todayCount);
  }
}
