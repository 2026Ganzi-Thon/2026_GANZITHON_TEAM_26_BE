package com.hanol.domain.interaction.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 함께 쉬는 방 접속 현황 (Redis).
 *
 * <ul>
 *   <li>{@code interaction:rest-room:members} → ZSET, member=userId, score=마지막 신호 시각(epoch ms)
 *   <li>{@code interaction:rest-entered:{userId}} → 입장 시각(epoch ms). 입장·신호 때마다 TTL 10분을 갱신한다
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class RestRoomRepository {

  private static final String MEMBERS_KEY = "interaction:rest-room:members";
  private static final String ENTERED_KEY_PREFIX = "interaction:rest-entered:";
  private static final Duration ENTERED_TTL = Duration.ofMinutes(10);
  private static final Duration ACTIVE_WINDOW = Duration.ofSeconds(60);

  private final StringRedisTemplate redisTemplate;

  public Optional<Instant> findEnteredAt(Long userId) {
    String value = redisTemplate.opsForValue().get(enteredKey(userId));
    return Optional.ofNullable(value).map(Long::parseLong).map(Instant::ofEpochMilli);
  }

  public void saveEnteredAt(Long userId, Instant enteredAt) {
    redisTemplate
        .opsForValue()
        .set(enteredKey(userId), String.valueOf(enteredAt.toEpochMilli()), ENTERED_TTL);
  }

  public void refreshEnteredAtTtl(Long userId) {
    redisTemplate.expire(enteredKey(userId), ENTERED_TTL);
  }

  public void signal(Long userId, Instant now) {
    redisTemplate.opsForZSet().add(MEMBERS_KEY, String.valueOf(userId), now.toEpochMilli());
  }

  /** 지금 인원 집계(ZSET)에 들어있는지. 퇴장했거나 신호가 끊겨 정리된 경우 false. */
  public boolean isMember(Long userId) {
    return redisTemplate.opsForZSet().score(MEMBERS_KEY, String.valueOf(userId)) != null;
  }

  public void remove(Long userId) {
    redisTemplate.opsForZSet().remove(MEMBERS_KEY, String.valueOf(userId));
  }

  /** 최근 60초 이내 신호를 보낸 인원을 세고, 그보다 오래된 항목은 정리한다. */
  public long countActive(Instant now) {
    long activeFromMillis = now.minus(ACTIVE_WINDOW).toEpochMilli();
    redisTemplate.opsForZSet().removeRangeByScore(MEMBERS_KEY, 0, activeFromMillis - 1);
    Long count = redisTemplate.opsForZSet().count(MEMBERS_KEY, activeFromMillis, Double.MAX_VALUE);
    return count == null ? 0 : count;
  }

  private String enteredKey(Long userId) {
    return ENTERED_KEY_PREFIX + userId;
  }
}
