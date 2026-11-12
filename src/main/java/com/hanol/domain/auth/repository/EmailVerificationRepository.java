package com.hanol.domain.auth.repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * 이메일 인증 임시 데이터 (Redis).
 *
 * <ul>
 *   <li>{@code auth:email-code:{email}} → 인증번호 (마지막 발송분만 유효)
 *   <li>{@code auth:email-verified:{email}} → 인증 완료 표시
 *   <li>{@code auth:email-send-count:{email}} → 첫 발송부터 센 발송 횟수
 *   <li>{@code auth:email-verify-fail:{email}} → 첫 실패부터 센 인증 실패 횟수
 *   <li>{@code auth:email-blocked:{email}} → 발송·확인 차단 표시
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class EmailVerificationRepository {

  private static final String CODE_KEY_PREFIX = "auth:email-code:";
  private static final String VERIFIED_KEY_PREFIX = "auth:email-verified:";
  private static final String SEND_COUNT_KEY_PREFIX = "auth:email-send-count:";
  private static final String VERIFY_FAIL_KEY_PREFIX = "auth:email-verify-fail:";
  private static final String BLOCKED_KEY_PREFIX = "auth:email-blocked:";
  private static final String FLAG_VALUE = "true";

  // 첫 증가 때만 만료를 건다. (INCR 과 PEXPIRE 를 한 번에 실행)
  private static final RedisScript<Long> INCREMENT_SCRIPT =
      RedisScript.of(
          """
          local count = redis.call('INCR', KEYS[1])
          if count == 1 then
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
          end
          return count
          """,
          Long.class);

  private final StringRedisTemplate redisTemplate;

  /** 인증번호를 저장한다. 이전 번호는 덮어쓴다. */
  public void saveCode(String email, String code, Duration ttl) {
    redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, code, ttl);
  }

  public Optional<String> findCode(String email) {
    return Optional.ofNullable(redisTemplate.opsForValue().get(CODE_KEY_PREFIX + email));
  }

  public void deleteCode(String email) {
    redisTemplate.delete(CODE_KEY_PREFIX + email);
  }

  public void saveVerified(String email, Duration ttl) {
    redisTemplate.opsForValue().set(VERIFIED_KEY_PREFIX + email, FLAG_VALUE, ttl);
  }

  public boolean isVerified(String email) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(VERIFIED_KEY_PREFIX + email));
  }

  public void deleteVerified(String email) {
    redisTemplate.delete(VERIFIED_KEY_PREFIX + email);
  }

  /** 발송 횟수를 1 늘리고 늘어난 값을 돌려준다. 만료는 첫 발송 기준. */
  public long increaseSendCount(String email, Duration window) {
    return increment(SEND_COUNT_KEY_PREFIX + email, window);
  }

  public void deleteSendCount(String email) {
    redisTemplate.delete(SEND_COUNT_KEY_PREFIX + email);
  }

  /** 인증 실패 횟수를 1 늘리고 늘어난 값을 돌려준다. 만료는 첫 실패 기준. */
  public long increaseVerifyFailCount(String email, Duration window) {
    return increment(VERIFY_FAIL_KEY_PREFIX + email, window);
  }

  public void deleteVerifyFailCount(String email) {
    redisTemplate.delete(VERIFY_FAIL_KEY_PREFIX + email);
  }

  public void block(String email, Duration ttl) {
    redisTemplate.opsForValue().set(BLOCKED_KEY_PREFIX + email, FLAG_VALUE, ttl);
  }

  public boolean isBlocked(String email) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(BLOCKED_KEY_PREFIX + email));
  }

  private long increment(String key, Duration ttl) {
    Long count =
        redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(ttl.toMillis()));
    return count == null ? 0 : count;
  }
}
