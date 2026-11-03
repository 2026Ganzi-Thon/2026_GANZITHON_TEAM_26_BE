package com.hanol.global.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 허용 출처 (app.cors.allowed-origins).
 * 앱(안드로이드)에서 보내는 요청에는 CORS 가 적용되지 않으므로, 웹 미리보기·Swagger 등 브라우저 요청용이다.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
