package com.hanol.domain.auth.dto.request;

/**
 * 토큰 재발급 요청. spec(USR-AUTH-006)상 Refresh 토큰이 없으면 400 이 아니라 401 이어야 하므로 Bean Validation 을 걸지 않고
 * 서비스에서 검사한다.
 */
public record TokenReissueRequest(String refreshToken) {}
