package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hanol.domain.cheer.dto.response.CheerAvailabilityResponse;
import com.hanol.domain.cheer.dto.response.EmotionQuestionGroup;
import com.hanol.domain.cheer.entity.CheerStatus;
import com.hanol.domain.cheer.repository.CheerRepository;
import com.hanol.domain.cheer.repository.CheerRepository.EmotionCount;
import com.hanol.domain.mood.entity.Emotion;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheerServiceTest {

  @Mock private CheerRepository cheerRepository;

  private CheerService cheerService;

  @BeforeEach
  void setUp() {
    cheerService = new CheerService(cheerRepository);
  }

  @Test
  @DisplayName("sendable 은 true 이고 감정 11개가 모두 있다")
  void availabilityContainsAllEmotions() {
    given(cheerRepository.countGroupByTargetEmotion(CheerStatus.WAITING)).willReturn(List.of());

    CheerAvailabilityResponse response = cheerService.getAvailability();

    assertThat(response.sendable()).isTrue();
    assertThat(response.questions())
        .hasSize(11)
        .extracting(EmotionQuestionGroup::emotion)
        .containsExactlyInAnyOrder(Emotion.values());
  }

  @Test
  @DisplayName("대기 수가 적은 감정부터, 같으면 Emotion 선언 순서로 정렬된다")
  void sortByWaitingCountThenDeclarationOrder() {
    given(cheerRepository.countGroupByTargetEmotion(CheerStatus.WAITING))
        .willReturn(List.of(count(Emotion.LONELY, 2), count(Emotion.ANXIETY, 1)));

    CheerAvailabilityResponse response = cheerService.getAvailability();

    assertThat(response.questions())
        .extracting(EmotionQuestionGroup::emotion)
        .containsExactly(
            Emotion.UNMOTIVATED,
            Emotion.HOPELESS_FUTURE,
            Emotion.EXHAUSTED,
            Emotion.INADEQUATE,
            Emotion.NOWHERE_TO_VENT,
            Emotion.TEARFUL,
            Emotion.RESTLESS,
            Emotion.OVERWHELMED,
            Emotion.UNSURE,
            Emotion.ANXIETY,
            Emotion.LONELY);
  }

  @Test
  @DisplayName("각 감정의 questions 는 그 감정의 cheerQuestion 1개")
  void questionsAreCheerQuestionOfEachEmotion() {
    given(cheerRepository.countGroupByTargetEmotion(CheerStatus.WAITING)).willReturn(List.of());

    CheerAvailabilityResponse response = cheerService.getAvailability();

    assertThat(response.questions())
        .allSatisfy(
            group ->
                assertThat(group.questions()).containsExactly(group.emotion().getCheerQuestion()));
  }

  @Test
  @DisplayName("대기 수는 WAITING 상태로만 센다")
  void countsOnlyWaitingCheers() {
    given(cheerRepository.countGroupByTargetEmotion(CheerStatus.WAITING)).willReturn(List.of());

    cheerService.getAvailability();

    verify(cheerRepository).countGroupByTargetEmotion(CheerStatus.WAITING);
  }

  private static EmotionCount count(Emotion emotion, long count) {
    return new EmotionCount() {
      @Override
      public Emotion getEmotion() {
        return emotion;
      }

      @Override
      public long getCount() {
        return count;
      }
    };
  }
}
