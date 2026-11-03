package com.hanol.infra.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 한 올 메시지 검수(정상/부적절/위기 분류)와 사연 위기 표현 감지에 사용할 AI API 설정 (app.ai.*).
 * 어떤 AI 서비스를 쓸지 정해지면 이 패키지에 호출 클라이언트를 추가한다.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
	String apiKey,
	String baseUrl,
	String model) {
}
