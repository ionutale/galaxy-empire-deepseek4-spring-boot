package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.ShipyardQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ShipyardQueueRepository extends JpaRepository<ShipyardQueue, Long> {
    List<ShipyardQueue> findByPlanetIdAndCompletedFalseOrderByStartedAt(Long planetId);
    List<ShipyardQueue> findByCompletedFalseAndCompletesAtLessThanEqual(Instant now);
}
