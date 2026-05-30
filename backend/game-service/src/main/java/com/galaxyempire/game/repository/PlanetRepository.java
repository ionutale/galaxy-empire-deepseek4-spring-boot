package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.Planet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanetRepository extends JpaRepository<Planet, Long> {
    Optional<Planet> findById(Long id);
    Optional<Planet> findByPlayerId(Long playerId);
    boolean existsByGalaxyAndSystemIdAndSlot(int galaxy, int systemId, int slot);
    List<Planet> findByPlayerIdOrderByCreatedAt(Long playerId);

    @Query("SELECT p.systemId FROM Planet p WHERE p.galaxy = :galaxy")
    List<Integer> findSystemIdsByGalaxy(@Param("galaxy") int galaxy);

    List<Planet> findByGalaxyAndSystemId(int galaxy, int systemId);
}
