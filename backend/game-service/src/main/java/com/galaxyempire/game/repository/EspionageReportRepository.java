package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.EspionageReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EspionageReportRepository extends JpaRepository<EspionageReport, Long> {
    List<EspionageReport> findByTargetPlanetIdOrderByTimestampDesc(Long targetPlanetId);
    List<EspionageReport> findByDefenderIdOrderByTimestampDesc(Long defenderId);
}
