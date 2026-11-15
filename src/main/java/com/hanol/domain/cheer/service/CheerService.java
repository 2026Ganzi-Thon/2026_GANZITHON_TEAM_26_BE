package com.hanol.domain.cheer.service;

import com.hanol.domain.cheer.dto.response.CheerAvailabilityResponse;
import com.hanol.domain.cheer.dto.response.EmotionQuestionGroup;
import com.hanol.domain.cheer.entity.CheerStatus;
import com.hanol.domain.cheer.repository.CheerRepository;
import com.hanol.domain.cheer.repository.CheerRepository.EmotionCount;
import com.hanol.domain.mood.entity.Emotion;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheerService {

  private final CheerRepository cheerRepository;

  /** BLK-SEND-001 보내기 가능 확인. 대기 중인 패치가 적은 감정부터, 같으면 Emotion 선언 순서. */
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
}
