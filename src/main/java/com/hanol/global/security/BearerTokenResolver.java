package com.hanol.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/** {@code Authorization: Bearer {token}} 헤더에서 토큰 문자열을 꺼낸다. */
public final class BearerTokenResolver {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private BearerTokenResolver() {}

  /** 헤더가 없거나 Bearer 형식이 아니면 null. */
  public static String resolve(HttpServletRequest request) {
    String header = request.getHeader(AUTHORIZATION_HEADER);
    if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }
}
