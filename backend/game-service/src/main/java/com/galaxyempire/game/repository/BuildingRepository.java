package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.Building;
import com.galaxyempire.game.domain.BuildingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuildingRepository extends JpaRepository<Building, Long> {
    List<Building> findByPlanetId(Long planetId);
    Optional<Building> findByPlanetIdAndGridPosition(Long planetId, int gridPosition);
    Optional<Building> findByPlanetIdAndBuildingType(Long planetId, BuildingType buildingType);
}
