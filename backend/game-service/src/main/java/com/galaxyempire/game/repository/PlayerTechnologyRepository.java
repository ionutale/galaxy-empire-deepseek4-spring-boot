package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.PlayerTechnology;
import com.galaxyempire.game.domain.Technology;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerTechnologyRepository extends JpaRepository<PlayerTechnology, Long> {
    List<PlayerTechnology> findByPlayerId(Long playerId);
    Optional<PlayerTechnology> findByPlayerIdAndTechnology(Long playerId, Technology technology);
}
