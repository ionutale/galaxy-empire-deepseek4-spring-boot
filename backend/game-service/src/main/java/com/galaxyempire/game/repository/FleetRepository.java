package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.Fleet;
import com.galaxyempire.game.domain.FleetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface FleetRepository extends JpaRepository<Fleet, Long> {
    List<Fleet> findByOriginPlanetId(Long originPlanetId);
    List<Fleet> findByPlayerId(Long playerId);
    List<Fleet> findByStatusAndArrivalTimeLessThanEqual(FleetStatus status, Instant now);
    List<Fleet> findByStatusAndReturnTimeLessThanEqual(FleetStatus status, Instant now);
}
