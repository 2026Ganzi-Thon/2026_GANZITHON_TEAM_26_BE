package com.hanol.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Refresh 토큰 저장소 (Redis). 키: {@code auth:refresh:{userId}}. 사용자당 하나만 유지한다. */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

  private static final String KEY_PREFIX = "auth:refresh:";

  private final StringRedisTemplate redisTemplate;

  public void save(Long userId, String refreshToken, Duration ttl) {
    redisTemplate.opsForValue().set(KEY_PREFIX + userId, refreshToken, ttl);
  }

  public Optional<String> find(Long userId) {
    return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + userId));
  }

  public void delete(Long userId) {
    redisTemplate.delete(KEY_PREFIX + userId);
  }
}
