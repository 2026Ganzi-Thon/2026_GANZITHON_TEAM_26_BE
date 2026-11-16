package com.hanol.domain.blanket.repository;

import com.hanol.domain.blanket.entity.Blanket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlanketRepository extends JpaRepository<Blanket, Long> {

  Optional<Blanket> findFirstByUserIdAndCompletedAtIsNullOrderByIdDesc(Long userId);

  @Query("select b.id from Blanket b where b.user.id = :userId order by b.id asc")
  List<Long> findIdsByUserIdOrderByIdAsc(@Param("userId") Long userId);
}
