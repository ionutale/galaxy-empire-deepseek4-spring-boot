package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.ResearchQueue;
import com.galaxyempire.game.domain.Technology;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ResearchQueueRepository extends JpaRepository<ResearchQueue, Long> {
    List<ResearchQueue> findByPlayerIdAndCompletedFalse(Long playerId);
    Optional<ResearchQueue> findByPlayerIdAndCompletedFalseAndTechnology(Long playerId, Technology technology);
    List<ResearchQueue> findByCompletedFalseAndCompletesAtLessThanEqual(Instant now);
    boolean existsByPlayerIdAndCompletedFalse(Long playerId);
}
