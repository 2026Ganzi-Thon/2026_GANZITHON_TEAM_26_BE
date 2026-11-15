package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hanol.domain.alert.service.CrisisKeywordMatcher;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.CommonErrorCode;
import com.hanol.infra.ai.AiChatClient;
import com.hanol.infra.ai.AiClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AiCheerReviewerTest {

  @Mock private AiChatClient aiChatClient;

  private AiCheerReviewer reviewer;

  @BeforeEach
  void setUp() {
    reviewer =
        new AiCheerReviewer(
            new CrisisKeywordMatcher(),
            new CheerContactMatcher(),
            aiChatClient,
            JsonMapper.builder().build());
  }

  @Test
  @DisplayName("연락처만 걸리면 AI 를 부르지 않고 CONTACT_OR_LINK")
  void contactOnlySkipsAi() {
    assertThat(reviewer.review("010-1234-5678로 연락해요")).isEqualTo(CheerReviewResult.CONTACT_OR_LINK);

    verifyNoInteractions(aiChatClient);
  }

  @ParameterizedTest
  @EnumSource(CheerReviewResult.class)
  @DisplayName("연락처가 없으면 AI 결과를 그대로 쓴다")
  void mapsAiResult(CheerReviewResult aiResult) {
    givenAiReturns("{\"result\":\"" + aiResult.name() + "\"}");

    assertThat(reviewer.review("산책하면 좀 나아졌어요")).isEqualTo(aiResult);
  }

  @Test
  @DisplayName("위험 단어와 연락처가 같이 있고 AI 가 CRISIS 면 CRISIS")
  void crisisWinsOverContact() {
    givenAiReturns("{\"result\":\"CRISIS\"}");

    assertThat(reviewer.review("요즘도 죽고 싶어요 010-1234-5678")).isEqualTo(CheerReviewResult.CRISIS);
  }

  @Test
  @DisplayName("위험 단어와 연락처가 같이 있고 AI 가 NORMAL 이면 CONTACT_OR_LINK")
  void contactWinsOverNormal() {
    givenAiReturns("{\"result\":\"NORMAL\"}");

    assertThat(reviewer.review("죽고 싶었는데 버텼어요 010-1234-5678"))
        .isEqualTo(CheerReviewResult.CONTACT_OR_LINK);
  }

  @Test
  @DisplayName("위험 단어에 걸렸는지 AI 요청에 같이 보낸다")
  void sendsKeywordMatchedToAi() {
    givenAiReturns("{\"result\":\"NORMAL\"}");

    reviewer.review("저도 죽고 싶었던 때가 있었는데 산책하면서 버텼어요");

    assertThat(capturedUserContent())
        .contains("위험 단어 검사: 걸림")
        .contains("저도 죽고 싶었던 때가 있었는데 산책하면서 버텼어요");
  }

  @Test
  @DisplayName("위험 단어가 없으면 안 걸림으로 보낸다")
  void sendsKeywordNotMatchedToAi() {
    givenAiReturns("{\"result\":\"NORMAL\"}");

    reviewer.review("산책하면 좀 나아졌어요");

    assertThat(capturedUserContent()).contains("위험 단어 검사: 안 걸림");
  }

  @ParameterizedTest
  @ValueSource(strings = {"산책하면 좀 나아졌어요", "요즘도 죽고 싶어요"})
  @DisplayName("AI 호출이 실패하면 500 (위험 단어 문장도)")
  void aiFailureIsServerError(String content) {
    given(aiChatClient.complete(anyString(), anyString(), anyString(), anyMap()))
        .willThrow(new AiClientException("timeout"));

    assertServerError(content);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json",
        "{}",
        "{\"result\":\"SAFE\"}",
        "{\"result\":\"normal\"}",
        "{\"result\":true}",
        "{\"result\":null}"
      })
  @DisplayName("AI 응답을 해석할 수 없거나 8개 중 하나가 아니면 500")
  void invalidAiResponseIsServerError(String response) {
    givenAiReturns(response);

    assertServerError("산책하면 좀 나아졌어요");
  }

  @Test
  @DisplayName("위험 단어 문장에서 AI 응답이 이상해도 500")
  void invalidAiResponseWithKeywordIsServerError() {
    givenAiReturns("{\"result\":\"SAFE\"}");

    assertServerError("요즘도 죽고 싶어요");
  }

  private void givenAiReturns(String response) {
    given(aiChatClient.complete(anyString(), anyString(), anyString(), anyMap()))
        .willReturn(response);
  }

  private String capturedUserContent() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(aiChatClient)
        .complete(eq(AiCheerReviewer.SYSTEM_PROMPT), captor.capture(), anyString(), anyMap());
    return captor.getValue();
  }

  private void assertServerError(String content) {
    assertThatThrownBy(() -> reviewer.review(content))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
  }
}
