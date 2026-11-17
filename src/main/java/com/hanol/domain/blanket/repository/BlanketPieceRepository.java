package com.hanol.domain.blanket.repository;

import com.hanol.domain.blanket.entity.BlanketPiece;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlanketPieceRepository extends JpaRepository<BlanketPiece, Long> {

  int countByBlanketId(Long blanketId);

  @Query(
      "select distinct p.content from BlanketPiece p"
          + " where p.blanket.user.id = :userId and p.cheer is null")
  List<String> findDefaultContents(@Param("userId") Long userId);

  /** 이미 받은 기본 메시지 문장들. 중복 판단 기준(앞뒤 공백 제거)을 한 곳에서 관리한다. */
  default Set<String> findReceivedDefaultMessages(Long userId) {
    return findDefaultContents(userId).stream().map(String::strip).collect(Collectors.toSet());
  }

  @Query("select p.slotIndex from BlanketPiece p where p.blanket.id = :blanketId")
  List<Integer> findSlotIndexesByBlanketId(@Param("blanketId") Long blanketId);

  @Query(
      "select p from BlanketPiece p left join fetch p.cheer"
          + " where p.blanket.id = :blanketId and p.slotIndex = :slotIndex")
  Optional<BlanketPiece> findWithCheerByBlanketIdAndSlotIndex(
      @Param("blanketId") Long blanketId, @Param("slotIndex") int slotIndex);

  Optional<BlanketPiece> findFirstByCheerIdAndBlanketUserId(Long cheerId, Long userId);

  boolean existsByCheerIdAndBlanketUserId(Long cheerId, Long userId);
}
