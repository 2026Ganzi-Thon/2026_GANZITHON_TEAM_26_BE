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

  /** 행이 없으면 넣고, 있으면 배타 락을 잡는다. */
  @Modifying
  @Query(
      value =
          "insert into send_restrictions (sender_hash, created_at, updated_at)"
              + " values (:senderHash, :now, :now)"
              + " on duplicate key update sender_hash = sender_hash",
      nativeQuery = true)
  int insertOrLock(@Param("senderHash") String senderHash, @Param("now") LocalDateTime now);
}
