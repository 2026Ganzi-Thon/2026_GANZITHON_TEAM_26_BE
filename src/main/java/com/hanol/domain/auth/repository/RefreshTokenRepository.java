package com.hanol.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Refresh 토큰 저장소 (Redis). 키: {@code auth:refresh:{userId}:{sessionId}}. 로그인(기기)마다 따로 유지한다. */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

  private static final String KEY_PREFIX = "auth:refresh:";

  private final StringRedisTemplate redisTemplate;

  public void save(Long userId, String sessionId, String refreshToken, Duration ttl) {
    redisTemplate.opsForValue().set(key(userId, sessionId), refreshToken, ttl);
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
