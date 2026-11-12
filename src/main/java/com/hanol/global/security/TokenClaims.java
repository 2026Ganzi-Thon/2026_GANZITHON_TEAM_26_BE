package com.hanol.global.security;

/** 검증된 토큰에서 꺼낸 값. sessionId 는 로그인(기기)마다 새로 만들어 Access·Refresh 토큰에 함께 담는다. */
public record TokenClaims(Long userId, String sessionId) {}
