package com.hanol.domain.cheer.service;

import com.hanol.domain.mood.entity.Emotion;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** AI 로 기본 메시지를 만들지 못했을 때 쓰는 고정 대체 문구. 감정마다 3개. */
@Component
public class CheerFallbackMessages {

  private static final Map<Emotion, List<String>> MESSAGES = new EnumMap<>(Emotion.class);

  static {
    MESSAGES.put(
        Emotion.UNMOTIVATED,
        List.of("아무것도 안 한 날도 하루를 버틴 날이에요.", "잠깐 쉬어가도 아무도 뭐라 안 해요.", "오늘은 숨 쉬는 것만으로도 충분해요."));
    MESSAGES.put(
        Emotion.HOPELESS_FUTURE,
        List.of("막막한 밤도 결국 지나가더라고요.", "길이 안 보일 땐 한 걸음만 봐도 괜찮아요.", "지금 모른다고 앞으로도 모르는 건 아니에요."));
    MESSAGES.put(
        Emotion.ANXIETY,
        List.of(
            "걱정되는 마음, 그만큼 잘하고 싶었던 거예요.", "천천히 숨 한 번 쉬고 가요. 같이 있을게요.", "아직 일어나지 않은 일은 아직 오지 않았어요."));
    MESSAGES.put(
        Emotion.LONELY,
        List.of("지금 이 글을 보는 당신, 혼자가 아니에요.", "누군가는 오늘 당신을 생각하고 있어요.", "외로운 밤에도 같은 하늘 아래 우리가 있어요."));
    MESSAGES.put(
        Emotion.EXHAUSTED,
        List.of("오늘 정말 애썼어요. 이제 좀 쉬어도 돼요.", "지친 건 그만큼 열심히 살았다는 뜻이에요.", "잠시 내려놓아도 다 무너지지 않아요."));
    MESSAGES.put(
        Emotion.INADEQUATE,
        List.of(
            "지금 모습 그대로도 충분히 괜찮은 사람이에요.", "남과 비교하지 않아도 당신은 당신이에요.", "부족하다고 느끼는 건 더 나아지고 싶어서예요."));
    MESSAGES.put(
        Emotion.NOWHERE_TO_VENT,
        List.of(
            "말할 곳 없던 마음, 여기 잠깐 내려놓아요.", "화가 나는 건 그만큼 소중한 게 있어서예요.", "참느라 고생했어요. 그 마음 충분히 이해해요."));
    MESSAGES.put(
        Emotion.TEARFUL,
        List.of("울고 싶을 땐 울어도 괜찮아요.", "눈물은 마음이 쉬어가는 방법이래요.", "실컷 울고 나면 조금은 가벼워질 거예요."));
    MESSAGES.put(
        Emotion.RESTLESS,
        List.of("쉬지 못한 하루도 끝까지 버틴 거예요.", "오늘은 5분만이라도 나를 위해 써봐요.", "바쁜 와중에도 숨 고를 틈은 꼭 챙겨요."));
    MESSAGES.put(
        Emotion.OVERWHELMED,
        List.of("사람들과 잠시 거리를 둬도 괜찮아요.", "혼자 있는 시간도 나를 지키는 방법이에요.", "모두에게 맞추지 않아도 괜찮아요."));
    MESSAGES.put(
        Emotion.UNSURE,
        List.of("내 마음을 몰라도 괜찮아요. 천천히 알아가요.", "이름 붙이기 어려운 날도 있는 거예요.", "모르겠는 마음도 그대로 소중한 마음이에요."));
  }

  public List<String> of(Emotion emotion) {
    return MESSAGES.get(emotion);
  }

  /** 이미 받은 문구는 빼고 무작위로 고른다. 다 받았으면 전체에서 고른다. */
  public String pick(Emotion emotion, Collection<String> receivedMessages) {
    List<String> candidates = of(emotion);
    List<String> notReceived =
        candidates.stream().filter(message -> !receivedMessages.contains(message)).toList();
    List<String> pool = notReceived.isEmpty() ? candidates : notReceived;
    return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
  }
}
