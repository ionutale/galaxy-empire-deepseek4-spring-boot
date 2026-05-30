package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.CombatReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CombatReportRepository extends JpaRepository<CombatReport, Long> {
    @Query("SELECT r FROM CombatReport r WHERE r.attackerPlanetId = :planetId OR r.defenderPlanetId = :planetId ORDER BY r.timestamp DESC")
    List<CombatReport> findByPlanetId(@Param("planetId") Long planetId);
}
