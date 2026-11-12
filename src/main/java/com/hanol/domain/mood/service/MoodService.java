package com.hanol.domain.mood.service;

import com.hanol.domain.alert.service.CrisisDetector;
import com.hanol.domain.interaction.entity.WarmthMapLog;
import com.hanol.domain.interaction.repository.WarmthMapLogRepository;
import com.hanol.domain.mood.dto.request.MoodRecordCreateRequest;
import com.hanol.domain.mood.dto.request.StoryCheckRequest;
import com.hanol.domain.mood.dto.response.MoodCalendarResponse;
import com.hanol.domain.mood.dto.response.MoodDayListResponse;
import com.hanol.domain.mood.dto.response.MoodDayRecordResponse;
import com.hanol.domain.mood.dto.response.MoodDetailPatchResponse;
import com.hanol.domain.mood.dto.response.MoodDetailResponse;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  public StoryCheckResponse checkStory(StoryCheckRequest request) {
    return new StoryCheckResponse(crisisDetector.isCrisis(request.content()));
  }

  @Transactional
  public MoodRecordCreateResponse createRecord(Long userId, MoodRecordCreateRequest request) {
    List<Emotion> emotions = request.emotions();
    if (new HashSet<>(emotions).size() != emotions.size()) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    String story = request.story();
    boolean hasStory = story != null && !story.isBlank();
    if (hasStory && crisisDetector.isCrisis(story)) {
      log.info("Crisis expression detected in mood record story. userId={}", userId);
    }

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

    Integer remainingSendableCount = null;
    if (hasStory) {
      storyRepository.save(Story.builder().moodRecord(record).content(story).build());
      dailyStat.increaseBonusSendCount();
      remainingSendableCount = dailyStat.getRemainingSendableCount();
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
        record.getId(),
        emotions.stream().map(Emotion::name).toList(),
        now,
        todayCount,
        remainingSendableCount);
  }

  @Transactional(readOnly = true)
  public MoodCalendarResponse getCalendar(Long userId, YearMonth yearMonth) {
    return new MoodCalendarResponse(
        moodRecordRepository.findRecordDates(userId, yearMonth.atDay(1), yearMonth.atEndOfMonth()));
  }

  @Transactional(readOnly = true)
  public MoodDayListResponse getDayRecords(Long userId, LocalDate date) {
    List<MoodRecord> records =
        moodRecordRepository.findByUserIdAndRecordDateOrderByRecordedAtAsc(userId, date);
    if (records.isEmpty()) {
      throw new BusinessException(CommonErrorCode.NOT_FOUND);
    }

    List<Long> recordIds = records.stream().map(MoodRecord::getId).toList();
    Map<Long, List<String>> emotionsByRecordId =
        moodRecordCardRepository.findByMoodRecordIdInOrderByCardOrderAsc(recordIds).stream()
            .collect(
                Collectors.groupingBy(
                    card -> card.getMoodRecord().getId(),
                    Collectors.mapping(card -> card.getEmotion().name(), Collectors.toList())));
    Set<Long> storyRecordIds = new HashSet<>(storyRepository.findMoodRecordIds(recordIds));

    Integer sentPatchCount =
        dailyStatRepository
            .findByUserIdAndStatDate(userId, date)
            .map(DailyStat::getSentCount)
            .filter(count -> count > 0)
            .orElse(null);

    return new MoodDayListResponse(
        sentPatchCount,
        records.stream()
            .map(
                record ->
                    new MoodDayRecordResponse(
                        record.getId(),
                        record.getRecordedAt(),
                        emotionsByRecordId.getOrDefault(record.getId(), List.of()),
                        storyRecordIds.contains(record.getId())))
            .toList());
  }

  @Transactional(readOnly = true)
  public MoodDetailResponse getRecord(Long userId, Long recordId) {
    MoodRecord record =
        moodRecordRepository
            .findById(recordId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    if (!Objects.equals(record.getUser().getId(), userId)) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    List<MoodRecordCard> cards =
        moodRecordCardRepository.findByMoodRecordIdOrderByCardOrderAsc(recordId);
    String story = storyRepository.findByMoodRecordId(recordId).map(Story::getContent).orElse(null);

    return new MoodDetailResponse(
        cards.stream().map(card -> card.getEmotion().name()).toList(),
        cards.stream()
            .map(
                card ->
                    new MoodDetailPatchResponse(
                        card.getEmotion().name(), card.getReceiveStatus(), null))
            .toList(),
        story);
  }
}
