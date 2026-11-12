package com.hanol.global.security;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 세션별 현재 Access 토큰 (Redis). 키: {@code auth:access:{userId}:{sessionId}}. 저장된 토큰만 유효하며, 재발급하면 덮어써져
 * 이전 토큰은 바로 무효가 된다.
 */
@Component
@RequiredArgsConstructor
public class AccessTokenStore {

  private static final String KEY_PREFIX = "auth:access:";

  private final StringRedisTemplate redisTemplate;

  public void save(Long userId, String sessionId, String accessToken, Duration ttl) {
    redisTemplate.opsForValue().set(key(userId, sessionId), accessToken, ttl);
  }

  public Optional<String> find(Long userId, String sessionId) {
    return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId, sessionId)));
  }

  public void delete(Long userId, String sessionId) {
    redisTemplate.delete(key(userId, sessionId));
  }

  private static String key(Long userId, String sessionId) {
    return KEY_PREFIX + userId + ":" + sessionId;
  }
}
