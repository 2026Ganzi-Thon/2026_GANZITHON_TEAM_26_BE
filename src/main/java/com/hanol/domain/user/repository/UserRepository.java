package com.hanol.domain.user.repository;

import com.hanol.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  /**
   * 로그인 실패 횟수를 DB 에서 원자적으로 1 올리고, 이번 실패로 maxCount 에 도달하면 해제 시각을 기록한다.
   *
   * <p>MySQL 은 SET 절을 왼쪽부터 평가하므로 lockedUntil 을 먼저 계산해야 증가 전 값을 기준으로 비교한다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update User u
         set u.lockedUntil = case when u.failedLoginCount + 1 >= :maxCount
                                  then :lockedUntil else u.lockedUntil end,
             u.failedLoginCount = u.failedLoginCount + 1
       where u.id = :userId
      """)
  void increaseFailedLoginCount(
      @Param("userId") Long userId,
      @Param("maxCount") int maxCount,
      @Param("lockedUntil") LocalDateTime lockedUntil);
}
