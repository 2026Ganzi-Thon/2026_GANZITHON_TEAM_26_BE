package com.hanol.domain.cheer.repository;

import com.hanol.domain.cheer.entity.CheerReport;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheerReportRepository extends JpaRepository<CheerReport, Long> {

  long countBySenderHashAndReportedAtAfter(String senderHash, LocalDateTime since);
}
