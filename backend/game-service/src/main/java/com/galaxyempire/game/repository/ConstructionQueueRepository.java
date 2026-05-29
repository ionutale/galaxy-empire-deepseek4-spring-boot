package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.ConstructionQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ConstructionQueueRepository extends JpaRepository<ConstructionQueue, Long> {
    List<ConstructionQueue> findByPlanetIdAndCompletedFalseOrderByStartedAt(Long planetId);
    List<ConstructionQueue> findByCompletedFalseAndCompletesAtLessThanEqual(Instant now);
    long countByPlanetIdAndCompletedFalse(Long planetId);
}
