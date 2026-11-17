package com.hanol.domain.blanket.repository;

import com.hanol.domain.blanket.entity.BlanketPiece;
import com.hanol.domain.mood.entity.Emotion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlanketPieceRepository extends JpaRepository<BlanketPiece, Long> {

  int countByBlanketId(Long blanketId);

  @Query(
      "select distinct p.content from BlanketPiece p"
          + " where p.blanket.user.id = :userId and p.cheer is null")
  List<String> findDefaultContents(@Param("userId") Long userId);

  @Query(
      "select p.slotIndex as slotIndex, p.emotion as emotion from BlanketPiece p"
          + " where p.blanket.id = :blanketId")
  List<SlotEmotion> findSlotEmotionsByBlanketId(@Param("blanketId") Long blanketId);

  @Query(
      "select p from BlanketPiece p left join fetch p.cheer"
          + " where p.blanket.id = :blanketId and p.slotIndex = :slotIndex")
  Optional<BlanketPiece> findWithCheerByBlanketIdAndSlotIndex(
      @Param("blanketId") Long blanketId, @Param("slotIndex") int slotIndex);

  Optional<BlanketPiece> findFirstByCheerIdAndBlanketUserId(Long cheerId, Long userId);

  interface SlotEmotion {

    int getSlotIndex();

    Emotion getEmotion();
  }
}
