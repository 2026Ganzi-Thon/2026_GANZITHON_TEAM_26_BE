package com.hanol.global.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 로그아웃한 Access 토큰 블랙리스트 (Redis). 키: {@code auth:blacklist:{accessToken}}. 토큰의 남은 유효 시간만큼만 보관한다. */
@Component
@RequiredArgsConstructor
public class AccessTokenBlacklist {

  private static final String KEY_PREFIX = "auth:blacklist:";
  private static final String VALUE = "logout";

  private final StringRedisTemplate redisTemplate;

  public void add(String accessToken, Duration remainingValidity) {
    if (remainingValidity.isZero() || remainingValidity.isNegative()) {
      return;
    }
    redisTemplate.opsForValue().set(KEY_PREFIX + accessToken, VALUE, remainingValidity);
  }

  public boolean contains(String accessToken) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + accessToken));
  }
}
