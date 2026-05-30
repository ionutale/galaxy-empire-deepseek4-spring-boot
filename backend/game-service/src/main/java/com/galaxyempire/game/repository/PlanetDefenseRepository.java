package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.PlanetDefense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlanetDefenseRepository extends JpaRepository<PlanetDefense, Long> {
    List<PlanetDefense> findByPlanetId(Long planetId);
    Optional<PlanetDefense> findByPlanetIdAndDefenseType(Long planetId, com.galaxyempire.game.domain.DefenseType defenseType);
}
