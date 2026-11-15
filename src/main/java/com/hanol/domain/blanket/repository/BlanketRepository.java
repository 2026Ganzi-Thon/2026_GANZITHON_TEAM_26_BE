package com.hanol.domain.blanket.repository;

import com.hanol.domain.blanket.entity.Blanket;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlanketRepository extends JpaRepository<Blanket, Long> {

  Optional<Blanket> findFirstByUserIdAndCompletedAtIsNullOrderByIdDesc(Long userId);
}
