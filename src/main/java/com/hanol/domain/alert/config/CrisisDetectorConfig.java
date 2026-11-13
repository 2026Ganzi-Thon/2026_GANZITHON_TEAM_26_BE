package com.hanol.domain.alert.config;

import com.hanol.domain.alert.service.AiCrisisDetector;
import com.hanol.domain.alert.service.CrisisDetector;
import com.hanol.domain.alert.service.CrisisKeywordMatcher;
import com.hanol.domain.alert.service.StubCrisisDetector;
import com.hanol.infra.ai.AiChatClient;
import com.hanol.infra.ai.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
public class CrisisDetectorConfig {

  @Bean
  public CrisisDetector crisisDetector(
      AiProperties aiProperties,
      CrisisKeywordMatcher keywordMatcher,
      AiChatClient aiChatClient,
      JsonMapper jsonMapper) {
    if (!StringUtils.hasText(aiProperties.apiKey())) {
      log.warn("AI 키가 없어 위기 검사는 위험 단어 검사만 합니다.");
      return new StubCrisisDetector(keywordMatcher);
    }
    return new AiCrisisDetector(keywordMatcher, aiChatClient, jsonMapper);
  }
}
