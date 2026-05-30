package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.FleetShip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FleetShipRepository extends JpaRepository<FleetShip, Long> {
    List<FleetShip> findByFleetId(Long fleetId);
}
