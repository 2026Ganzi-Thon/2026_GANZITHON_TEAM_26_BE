package com.hanol.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 이메일 인증 임시 데이터 (Redis).
 *
 * <ul>
 *   <li>{@code auth:email-code:{email}} → 인증번호
 *   <li>{@code auth:email-verified:{email}} → 인증 완료 표시
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class EmailVerificationRepository {

  private static final String CODE_KEY_PREFIX = "auth:email-code:";
  private static final String VERIFIED_KEY_PREFIX = "auth:email-verified:";
  private static final String VERIFIED_VALUE = "true";

  private final StringRedisTemplate redisTemplate;

  /** 아직 유효한 인증번호가 없을 때만 저장한다. 이미 있으면 false. */
  public boolean saveCodeIfAbsent(String email, String code, Duration ttl) {
    Boolean saved = redisTemplate.opsForValue().setIfAbsent(CODE_KEY_PREFIX + email, code, ttl);
    return Boolean.TRUE.equals(saved);
  }

  public Optional<String> findCode(String email) {
    return Optional.ofNullable(redisTemplate.opsForValue().get(CODE_KEY_PREFIX + email));
  }

  public void deleteCode(String email) {
    redisTemplate.delete(CODE_KEY_PREFIX + email);
  }

  public void saveVerified(String email, Duration ttl) {
    redisTemplate.opsForValue().set(VERIFIED_KEY_PREFIX + email, VERIFIED_VALUE, ttl);
  }

  public boolean isVerified(String email) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(VERIFIED_KEY_PREFIX + email));
  }

  public void deleteVerified(String email) {
    redisTemplate.delete(VERIFIED_KEY_PREFIX + email);
  }
}
