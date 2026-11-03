package com.hanol.global.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 (app.jwt.*).
 * secret 은 Base64 로 인코딩된 32바이트 이상의 키여야 한다. 생성: openssl rand -base64 32
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
	String secret,
	Duration accessTokenValidity,
	Duration refreshTokenValidity) {
}
