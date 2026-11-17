package com.hanol.domain.cheer.service;

import com.hanol.domain.alert.service.CrisisKeywordMatcher;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.infra.ai.AiChatClient;
import com.hanol.infra.ai.AiClientException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 받을 때마다 AI 로 새 응원 문장을 만든다. 규칙 검사·중복에 걸리면 한 번만 다시 만들고, 그래도 안 되면 대체 문구. */
@Slf4j
@RequiredArgsConstructor
public class AiDefaultCheerMessageGenerator implements DefaultCheerMessageGenerator {

  static final String SYSTEM_PROMPT =
      """
      너는 정신건강 케어 앱 '패치'에서 힘든 마음을 고른 익명의 사람에게 건네는 응원 문장을 쓴다.
      입력으로 받는 사람이 고른 감정, 그 감정의 좋은 예시 문장, 이번에 쓸 수 있는 소재가 주어진다.

      길이 (가장 중요)
      - 공백·문장부호 포함 25~40자. 50자를 넘으면 쓸 수 없어 버려진다.
      - 한 문장이 좋고, 두 문장이면 둘 다 아주 짧게.
      - 길이 예시: "따뜻한 물 한 잔 마시고 잠깐 쉬어가요."(22자),
        "오늘 하루 버틴 것만으로도 충분해요. 푹 쉬어요."(27자)

      말투
      - 모든 문장은 ~요 또는 ~니다로 끝나는 존댓말로 쓴다.
      - 반말, '너'·'네' 같은 호칭은 쓰지 않는다.

      규칙
      - 주어진 소재는 자연스럽게 어울릴 때만 쓰고, 어색하면 쓰지 않는다. 말이 되는 문장이 소재보다 우선이다.
      - 비유(~처럼, ~듯)와 사물이 사람처럼 행동하는 표현(예: 이불이 기다려요)은 쓰지 말고, 직접적인 말로 쓴다.
      - 좋은 예시 문장의 톤과 길이를 참고하되, 그대로 베끼지 않는다.
      - 받는 사람의 감정을 부정·축소·비교·훈계하지 않는다("별거 아니에요", "다들 그래요", "~해야 해요" 금지).
      - 조언을 강요하지 않는다. 제안은 부담 없는 말투로만.
      - 연락처·링크·아이디, 자살·자해·술·약물 같은 위험한 표현을 쓰지 않는다.
      - 흔한 상투 문구는 피한다.
      - 문장만 message 에 담는다. 따옴표·이모지·해시태그는 쓰지 않는다.
      """;

  static final double TEMPERATURE = 0.9;
  static final int MAX_LENGTH = 50;
  private static final int MAX_ATTEMPTS = 2;
  private static final String SCHEMA_NAME = "default_cheer_message";
  static final Map<String, Object> SCHEMA =
      Map.of(
          "type",
          "object",
          "properties",
          Map.of("message", Map.of("type", "string")),
          "required",
          List.of("message"),
          "additionalProperties",
          false);

  static final List<String> TOPICS =
      List.of(
          "잠",
          "낮잠",
          "숨 고르기",
          "산책",
          "스트레칭",
          "샤워",
          "따뜻한 물 한 잔",
          "따뜻한 밥",
          "좋아하는 간식",
          "좋아하는 노래",
          "좋아하는 영상",
          "휴대폰 잠시 내려놓기",
          "방 창문 열고 환기하기",
          "햇볕 쬐기");

  private static final Pattern SENTENCE_DELIMITER = Pattern.compile("[.!?…\\n]+");
  private static final Pattern TRAILING_MARKS = Pattern.compile("[\\s.!?…~]+$");

  private final CrisisKeywordMatcher keywordMatcher;
  private final CheerContactMatcher contactMatcher;
  private final AiChatClient aiChatClient;
  private final JsonMapper jsonMapper;
  private final CheerFallbackMessages fallbackMessages;

  @Override
  public String generate(Emotion emotion, Set<String> receivedMessages) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      String message;
      try {
        message = request(emotion);
      } catch (AiClientException | JacksonException exception) {
        log.error(
            "Default cheer message generation failed. emotion={}, reason={}",
            emotion,
            exception instanceof JacksonException
                ? exception.getClass().getSimpleName()
                : exception.getMessage());
        return fallbackMessages.pick(emotion, receivedMessages);
      }

      String rejectReason = rejectReason(message, receivedMessages);
      if (rejectReason == null) {
        return message;
      }
      log.info(
          "Default cheer message rejected. emotion={}, attempt={}, reason={}",
          emotion,
          attempt,
          rejectReason);
    }
    return fallbackMessages.pick(emotion, receivedMessages);
  }

  private String request(Emotion emotion) {
    String topic = TOPICS.get(ThreadLocalRandom.current().nextInt(TOPICS.size()));
    String userContent =
        "받는 사람이 고른 감정: "
            + emotion.getLabel()
            + "\n이 감정에 대해 다른 사람들에게 묻는 질문(참고용): "
            + emotion.getCheerQuestion()
            + "\n이 감정의 좋은 예시 문장: "
            + String.join(" / ", fallbackMessages.of(emotion))
            + "\n이번 소재(어울릴 때만): "
            + topic;
    String response =
        aiChatClient.complete(SYSTEM_PROMPT, userContent, SCHEMA_NAME, SCHEMA, TEMPERATURE);
    JsonNode message = jsonMapper.readTree(response).path("message");
    if (!message.isString()) {
      throw new AiClientException("AI default cheer message is not a string");
    }
    return message.asString().strip();
  }

  String rejectReason(String message, Set<String> receivedMessages) {
    if (message.isEmpty()) {
      return "EMPTY";
    }
    if (message.length() > MAX_LENGTH) {
      return "TOO_LONG";
    }
    if (keywordMatcher.matches(message)) {
      return "CRISIS_KEYWORD";
    }
    if (contactMatcher.matches(message)) {
      return "CONTACT_OR_LINK";
    }
    if (!isHonorific(message)) {
      return "NOT_HONORIFIC";
    }
    if (message.contains("처럼") || message.contains("듯")) {
      return "FIGURATIVE";
    }
    if (receivedMessages.contains(message)) {
      return "ALREADY_RECEIVED";
    }
    return null;
  }

  static boolean isHonorific(String message) {
    for (String sentence : SENTENCE_DELIMITER.split(message)) {
      String body = TRAILING_MARKS.matcher(sentence).replaceAll("");
      if (body.isBlank()) {
        continue;
      }
      if (!body.endsWith("요") && !body.endsWith("니다")) {
        return false;
      }
    }
    return true;
  }
}
