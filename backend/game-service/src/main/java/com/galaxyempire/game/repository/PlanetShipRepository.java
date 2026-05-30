package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.PlanetShip;
import com.galaxyempire.game.domain.ShipType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanetShipRepository extends JpaRepository<PlanetShip, Long> {
    List<PlanetShip> findByPlanetId(Long planetId);
    Optional<PlanetShip> findByPlanetIdAndShipType(Long planetId, ShipType shipType);
}
