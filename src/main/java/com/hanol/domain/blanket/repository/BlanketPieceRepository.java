package com.hanol.domain.blanket.repository;

import com.hanol.domain.blanket.entity.BlanketPiece;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlanketPieceRepository extends JpaRepository<BlanketPiece, Long> {

  int countByBlanketId(Long blanketId);

  @Query(
      "select distinct p.content from BlanketPiece p"
          + " where p.blanket.user.id = :userId and p.cheer is null")
  List<String> findDefaultContents(@Param("userId") Long userId);
}
