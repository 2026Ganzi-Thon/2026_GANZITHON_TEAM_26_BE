package com.hanol.domain.interaction.service;

import com.hanol.domain.interaction.dto.response.EmotionCountResponse;
import com.hanol.domain.interaction.dto.response.InteractionMapResponse;
import com.hanol.domain.interaction.dto.response.InteractionMapStatus;
import com.hanol.domain.interaction.repository.WarmthMapLogRepository;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.mood.entity.MoodRecord;
import com.hanol.domain.mood.entity.MoodRecordCard;
import com.hanol.domain.mood.repository.MoodRecordCardRepository;
import com.hanol.domain.mood.repository.MoodRecordRepository;
import com.hanol.domain.user.entity.DailyStat;
import com.hanol.domain.user.repository.DailyStatRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InteractionMapService {

  /** 담요는 하루 3개까지 적립되고, 4번째 기록부터는 적립되지 않는다. */
  private static final int BLANKET_STACK_LIMIT = 4;

  private final DailyStatRepository dailyStatRepository;
  private final MoodRecordRepository moodRecordRepository;
  private final MoodRecordCardRepository moodRecordCardRepository;
  private final WarmthMapLogRepository warmthMapLogRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public InteractionMapResponse getMap(Long userId) {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDate today = now.toLocalDate();

    DailyStat dailyStat =
        dailyStatRepository
            .findByUserIdAndStatDate(userId, today)
            .filter(stat -> stat.getPickCount() > 0)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.BAD_REQUEST));

    MoodRecord latestRecord =
        moodRecordRepository
            .findFirstByUserIdAndRecordDateOrderByRecordedAtDesc(userId, today)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.BAD_REQUEST));

    List<Emotion> emotions =
        moodRecordCardRepository
            .findByMoodRecordIdOrderByCardOrderAsc(latestRecord.getId())
            .stream()
            .map(MoodRecordCard::getEmotion)
            .toList();

    boolean blanketNotStackedNotice = dailyStat.getPickCount() >= BLANKET_STACK_LIMIT;
    LocalDateTime activeFrom = now.minusHours(24);

    List<EmotionCountResponse> activeCounts = countEmotions(emotions, activeFrom, now, userId);
    if (activeCounts.stream().anyMatch(e -> e.count() > 0)) {
      return new InteractionMapResponse(
          InteractionMapStatus.ACTIVE, activeCounts, blanketNotStackedNotice);
    }

    List<EmotionCountResponse> quietCounts =
        countEmotions(emotions, now.minusDays(7), activeFrom, userId);
    if (quietCounts.stream().anyMatch(e -> e.count() > 0)) {
      return new InteractionMapResponse(
          InteractionMapStatus.QUIET, quietCounts, blanketNotStackedNotice);
    }

    return new InteractionMapResponse(
        InteractionMapStatus.FIRST_LIGHT, List.of(), blanketNotStackedNotice);
  }

  private List<EmotionCountResponse> countEmotions(
      List<Emotion> emotions, LocalDateTime from, LocalDateTime to, Long userId) {
    return emotions.stream()
        .map(
            emotion -> {
              long count = warmthMapLogRepository.countOtherUsers(emotion, from, to, userId);
              return new EmotionCountResponse(emotion.name(), (int) count);
            })
        .toList();
  }
}
