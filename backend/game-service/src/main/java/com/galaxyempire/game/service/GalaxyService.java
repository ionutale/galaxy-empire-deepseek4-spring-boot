package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GalaxyService {

    private final PlanetRepository planetRepository;
    private final PlanetShipRepository planetShipRepository;
    private final PlanetDefenseRepository planetDefenseRepository;
    private final DebrisFieldRepository debrisFieldRepository;

    public GalaxyService(PlanetRepository planetRepository,
                         PlanetShipRepository planetShipRepository,
                         PlanetDefenseRepository planetDefenseRepository,
                         DebrisFieldRepository debrisFieldRepository) {
        this.planetRepository = planetRepository;
        this.planetShipRepository = planetShipRepository;
        this.planetDefenseRepository = planetDefenseRepository;
        this.debrisFieldRepository = debrisFieldRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSystemList(int galaxy, Long playerId) {
        Set<Integer> occupied = new HashSet<>(planetRepository.findSystemIdsByGalaxy(galaxy));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int systemId = 1; systemId <= 500; systemId++) {
            boolean occupiedBySomeone = occupied.contains(systemId);
            int planetCount = occupiedBySomeone ? countPlanetsInSystem(galaxy, systemId) : 0;
            boolean hasOwnPlanet = occupiedBySomeone && hasPlayerPlanetInSystem(galaxy, systemId, playerId);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("systemId", systemId);
            entry.put("planetCount", planetCount);
            entry.put("hasOwnPlanet", hasOwnPlanet);
            result.add(entry);
        }
        return result;
    }

    private int countPlanetsInSystem(int galaxy, int systemId) {
        return planetRepository.findByGalaxyAndSystemId(galaxy, systemId).size();
    }

    private boolean hasPlayerPlanetInSystem(int galaxy, int systemId, Long playerId) {
        return planetRepository.findByGalaxyAndSystemId(galaxy, systemId).stream()
            .anyMatch(p -> p.getPlayerId().equals(playerId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSystemDetail(int galaxy, int systemId, Long playerId) {
        List<Planet> planets = planetRepository.findByGalaxyAndSystemId(galaxy, systemId);
        Map<Integer, Planet> planetBySlot = new HashMap<>();
        for (Planet p : planets) {
            planetBySlot.put(p.getSlot(), p);
        }

        List<Map<String, Object>> slots = new ArrayList<>();
        for (int slot = 1; slot <= 15; slot++) {
            Planet p = planetBySlot.get(slot);
            if (p == null) {
                slots.add(Map.of("slot", slot, "occupied", false));
            } else {
                int fleetCount = planetShipRepository.findByPlanetId(p.getId()).stream()
                    .mapToInt(PlanetShip::getQuantity).sum();
                int defenseCount = planetDefenseRepository.findByPlanetId(p.getId()).stream()
                    .mapToInt(PlanetDefense::getQuantity).sum();
                Optional<DebrisField> df = debrisFieldRepository.findByPlanetId(p.getId());
                double debrisMetal = df.map(DebrisField::getMetal).orElse(0.0);
                double debrisCrystal = df.map(DebrisField::getCrystal).orElse(0.0);

                Map<String, Object> slotEntry = new LinkedHashMap<>();
                slotEntry.put("slot", slot);
                slotEntry.put("occupied", true);
                slotEntry.put("planetId", p.getId());
                slotEntry.put("planetName", p.getName());
                slotEntry.put("playerId", p.getPlayerId());
                slotEntry.put("playerName", "Player " + p.getPlayerId());
                slotEntry.put("isOwn", p.getPlayerId().equals(playerId));
                slotEntry.put("fleetCount", fleetCount);
                slotEntry.put("defenseCount", defenseCount);
                slotEntry.put("debrisMetal", debrisMetal);
                slotEntry.put("debrisCrystal", debrisCrystal);
                slots.add(slotEntry);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("galaxy", galaxy);
        result.put("systemId", systemId);
        result.put("slots", slots);
        return result;
    }
}
