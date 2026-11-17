package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hanol.domain.alert.service.CrisisKeywordMatcher;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.infra.ai.AiChatClient;
import com.hanol.infra.ai.AiClientException;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AiDefaultCheerMessageGeneratorTest {

  private static final Emotion EMOTION = Emotion.LONELY;
  private static final String RECEIVED = "오늘 밤도 당신 곁에 누군가 있어요.";
  private static final String FRESH = "창밖 하늘 한 번 보면서 숨 돌려봐요.";

  @Mock private AiChatClient aiChatClient;

  private final CheerFallbackMessages fallbackMessages = new CheerFallbackMessages();
  private AiDefaultCheerMessageGenerator generator;

  @BeforeEach
  void setUp() {
    generator =
        new AiDefaultCheerMessageGenerator(
            new CrisisKeywordMatcher(),
            new CheerContactMatcher(),
            aiChatClient,
            JsonMapper.builder().build(),
            fallbackMessages);
  }

  private void givenAiReturns(String first, String... rest) {
    given(
            aiChatClient.complete(
                anyString(),
                anyString(),
                anyString(),
                anyMap(),
                eq(AiDefaultCheerMessageGenerator.TEMPERATURE)))
        .willReturn(json(first), Arrays.stream(rest).map(this::json).toArray(String[]::new));
  }

  private String json(String message) {
    return "{\"message\":\"" + message + "\"}";
  }

  private void verifyAiCalls(int count) {
    verify(aiChatClient, times(count))
        .complete(
            anyString(),
            anyString(),
            anyString(),
            anyMap(),
            eq(AiDefaultCheerMessageGenerator.TEMPERATURE));
  }

  private void assertFallback(String message) {
    assertThat(fallbackMessages.of(EMOTION)).contains(message);
  }

  @Test
  @DisplayName("규칙을 통과하면 AI 문장을 앞뒤 공백 없이 그대로 쓴다")
  void returnsAiMessage() {
    givenAiReturns("  " + FRESH + " ");

    assertThat(generator.generate(EMOTION, Set.of(RECEIVED))).isEqualTo(FRESH);
    verifyAiCalls(1);
  }

  @Test
  @DisplayName("이미 받은 문장과 같으면 한 번 다시 만든다")
  void regenerateWhenAlreadyReceived() {
    givenAiReturns(" " + RECEIVED, FRESH);

    assertThat(generator.generate(EMOTION, Set.of(RECEIVED))).isEqualTo(FRESH);
    verifyAiCalls(2);
  }

  @Test
  @DisplayName("다시 만든 문장도 이미 받은 문장이면 대체 문구")
  void fallbackWhenRegeneratedIsAlsoReceived() {
    givenAiReturns(RECEIVED, RECEIVED);

    assertFallback(generator.generate(EMOTION, Set.of(RECEIVED)));
    verifyAiCalls(2);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "오늘 하루도 정말 애쓰셨어요. 창밖 하늘 한 번 올려다보면서 천천히 숨 한 번 고르고 따뜻한 물도 한 잔 드세요.",
        "죽고 싶을 만큼 힘들어도 괜찮아요.",
        "힘들면 010-1234-5678 로 연락해요.",
        "여기 들어와 보세요 www.example.com"
      })
  @DisplayName("비었거나 50자 초과·위험 단어·연락처면 다시 만들고, 또 걸리면 대체 문구")
  void fallbackWhenRuleCheckFailsTwice(String invalid) {
    givenAiReturns(invalid, invalid);

    assertFallback(generator.generate(EMOTION, Set.of()));
    verifyAiCalls(2);
  }

  @Test
  @DisplayName("규칙에 한 번 걸려도 다시 만든 문장이 괜찮으면 그 문장을 쓴다")
  void regenerateAfterRuleCheckFails() {
    givenAiReturns("힘들면 010-1234-5678 로 연락해요.", FRESH);

    assertThat(generator.generate(EMOTION, Set.of())).isEqualTo(FRESH);
    verifyAiCalls(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"쉬어도 괜찮아.", "오늘도 고생했어. 푹 쉬어요."})
  @DisplayName("존댓말이 아닌 문장이 있으면 다시 만들고, 또 걸리면 대체 문구")
  void fallbackWhenNotHonorificTwice(String casual) {
    givenAiReturns(casual, casual);

    assertFallback(generator.generate(EMOTION, Set.of()));
    verifyAiCalls(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"오늘도 고생 많으셨어요. 푹 쉬세요.", "정말 수고 많으셨습니다. 푹 쉬어요!", "따뜻한 물 한 잔 마시고 쉬어요~"})
  @DisplayName("모든 문장이 존댓말이면 끝이 요!·요~여도 통과한다")
  void passesHonorific(String honorific) {
    givenAiReturns(honorific);

    assertThat(generator.generate(EMOTION, Set.of())).isEqualTo(honorific);
    verifyAiCalls(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"포근한 이불처럼 마음을 감싸 보세요.", "기지개 켜듯 천천히 쉬어요."})
  @DisplayName("비유(처럼·듯) 문장을 다시 만들어도 비유면 대체 문구")
  void fallbackWhenFigurativeTwice(String figurative) {
    givenAiReturns(figurative, figurative);

    assertFallback(generator.generate(EMOTION, Set.of()));
    verifyAiCalls(2);
  }

  @Test
  @DisplayName("비유(처럼) 문장을 다시 만든 문장이 괜찮으면 그 문장을 쓴다")
  void regenerateAfterFigurative() {
    givenAiReturns("포근한 이불처럼 마음을 감싸 보세요.", FRESH);

    assertThat(generator.generate(EMOTION, Set.of())).isEqualTo(FRESH);
    verifyAiCalls(2);
  }

  @Test
  @DisplayName("AI 예외면 다시 부르지 않고 바로 대체 문구")
  void fallbackOnAiException() {
    given(
            aiChatClient.complete(
                anyString(),
                anyString(),
                anyString(),
                anyMap(),
                eq(AiDefaultCheerMessageGenerator.TEMPERATURE)))
        .willThrow(new AiClientException("timeout"));

    assertFallback(generator.generate(EMOTION, Set.of()));
    verifyAiCalls(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not json", "{}", "{\"message\":null}", "{\"message\":3}"})
  @DisplayName("응답을 해석할 수 없으면 다시 부르지 않고 바로 대체 문구")
  void fallbackOnInvalidResponse(String response) {
    given(
            aiChatClient.complete(
                anyString(),
                anyString(),
                anyString(),
                anyMap(),
                eq(AiDefaultCheerMessageGenerator.TEMPERATURE)))
        .willReturn(response);

    assertFallback(generator.generate(EMOTION, Set.of()));
    verifyAiCalls(1);
  }
}
