package com.hanol.infra.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI API 설정 (app.ai.*). */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String apiKey, String baseUrl, String model) {}
