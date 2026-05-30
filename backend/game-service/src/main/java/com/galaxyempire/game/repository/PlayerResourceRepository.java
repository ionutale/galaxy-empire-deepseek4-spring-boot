package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.PlayerResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerResourceRepository extends JpaRepository<PlayerResource, Long> {
    Optional<PlayerResource> findByPlayerId(Long playerId);
}
