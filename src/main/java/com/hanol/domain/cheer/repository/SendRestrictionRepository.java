package com.hanol.domain.cheer.repository;

import com.hanol.domain.cheer.entity.SendRestriction;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SendRestrictionRepository extends JpaRepository<SendRestriction, Long> {

  Optional<SendRestriction> findBySenderHash(String senderHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from SendRestriction r where r.senderHash = :senderHash")
  Optional<SendRestriction> findBySenderHashForUpdate(@Param("senderHash") String senderHash);

  /** 행이 없을 때만 넣는다. 이미 있으면 아무것도 하지 않는다. */
  @Modifying
  @Query(
      value =
          "insert ignore into send_restrictions (sender_hash, created_at, updated_at)"
              + " values (:senderHash, :now, :now)",
      nativeQuery = true)
  int insertIfAbsent(@Param("senderHash") String senderHash, @Param("now") LocalDateTime now);
}
