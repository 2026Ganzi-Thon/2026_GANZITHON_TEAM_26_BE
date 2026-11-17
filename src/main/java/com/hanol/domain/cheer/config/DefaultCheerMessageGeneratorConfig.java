package com.hanol.domain.cheer.config;

import com.hanol.domain.alert.service.CrisisKeywordMatcher;
import com.hanol.domain.cheer.service.AiDefaultCheerMessageGenerator;
import com.hanol.domain.cheer.service.CheerContactMatcher;
import com.hanol.domain.cheer.service.CheerFallbackMessages;
import com.hanol.domain.cheer.service.DefaultCheerMessageGenerator;
import com.hanol.domain.cheer.service.StubDefaultCheerMessageGenerator;
import com.hanol.infra.ai.AiChatClient;
import com.hanol.infra.ai.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
public class DefaultCheerMessageGeneratorConfig {

  @Bean
  public DefaultCheerMessageGenerator defaultCheerMessageGenerator(
      AiProperties aiProperties,
      CrisisKeywordMatcher keywordMatcher,
      CheerContactMatcher contactMatcher,
      AiChatClient aiChatClient,
      JsonMapper jsonMapper,
      CheerFallbackMessages fallbackMessages) {
    if (!StringUtils.hasText(aiProperties.apiKey())) {
      log.warn("AI 키가 없어 기본 메시지는 대체 문구만 씁니다.");
      return new StubDefaultCheerMessageGenerator(fallbackMessages);
    }
    return new AiDefaultCheerMessageGenerator(
        keywordMatcher, contactMatcher, aiChatClient, jsonMapper, fallbackMessages);
  }
}
