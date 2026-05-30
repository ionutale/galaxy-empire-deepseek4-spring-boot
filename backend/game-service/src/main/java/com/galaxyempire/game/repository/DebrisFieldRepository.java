package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.DebrisField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DebrisFieldRepository extends JpaRepository<DebrisField, Long> {
    Optional<DebrisField> findByPlanetId(Long planetId);
}
