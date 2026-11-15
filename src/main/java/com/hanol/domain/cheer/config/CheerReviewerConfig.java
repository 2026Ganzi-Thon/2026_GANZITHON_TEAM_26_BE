package com.hanol.domain.cheer.config;

import com.hanol.domain.alert.service.CrisisKeywordMatcher;
import com.hanol.domain.cheer.service.AiCheerReviewer;
import com.hanol.domain.cheer.service.CheerContactMatcher;
import com.hanol.domain.cheer.service.CheerReviewer;
import com.hanol.domain.cheer.service.StubCheerReviewer;
import com.hanol.infra.ai.AiChatClient;
import com.hanol.infra.ai.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
public class CheerReviewerConfig {

  @Bean
  public CheerReviewer cheerReviewer(
      AiProperties aiProperties,
      CrisisKeywordMatcher keywordMatcher,
      CheerContactMatcher contactMatcher,
      AiChatClient aiChatClient,
      JsonMapper jsonMapper) {
    if (!StringUtils.hasText(aiProperties.apiKey())) {
      log.warn("AI 키가 없어 패치 검수는 위험 단어·연락처 검사만 합니다.");
      return new StubCheerReviewer(keywordMatcher, contactMatcher);
    }
    return new AiCheerReviewer(keywordMatcher, contactMatcher, aiChatClient, jsonMapper);
  }
}
