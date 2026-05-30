package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ShipyardService {

    private final PlanetShipRepository planetShipRepository;
    private final ShipyardQueueRepository shipyardQueueRepository;
    private final BuildingRepository buildingRepository;
    private final PlanetRepository planetRepository;
    private final GameBalancer gameBalancer;
    private final PlanetDefenseRepository planetDefenseRepository;
    private final EconomyService economyService;
    private final DarkMatterService darkMatterService;

    public ShipyardService(PlanetShipRepository planetShipRepository,
                           ShipyardQueueRepository shipyardQueueRepository,
                           BuildingRepository buildingRepository,
                           PlanetRepository planetRepository,
                           GameBalancer gameBalancer,
                           PlanetDefenseRepository planetDefenseRepository,
                           EconomyService economyService,
                           DarkMatterService darkMatterService) {
        this.planetShipRepository = planetShipRepository;
        this.shipyardQueueRepository = shipyardQueueRepository;
        this.buildingRepository = buildingRepository;
        this.planetRepository = planetRepository;
        this.gameBalancer = gameBalancer;
        this.planetDefenseRepository = planetDefenseRepository;
        this.economyService = economyService;
        this.darkMatterService = darkMatterService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getShipTypes(Long planetId) {
        int shipyardLevel = buildingRepository
            .findByPlanetIdAndGridPosition(planetId, 9)
            .map(Building::getLevel)
            .orElse(0);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ShipType type : ShipType.values()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("shipType", type.name());
            entry.put("metalCost", gameBalancer.getShipMetalCost(type));
            entry.put("crystalCost", gameBalancer.getShipCrystalCost(type));
            entry.put("gasCost", gameBalancer.getShipGasCost(type));
            entry.put("timeSeconds", gameBalancer.getShipBuildTimeSeconds(type, shipyardLevel, 0));
            entry.put("requiredShipyardLevel", gameBalancer.getRequiredShipyardLevel(type));
            entry.put("available", shipyardLevel >= gameBalancer.getRequiredShipyardLevel(type));
            result.add(entry);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDefenseTypes(Long planetId) {
        int shipyardLevel = buildingRepository
            .findByPlanetIdAndGridPosition(planetId, 9)
            .map(Building::getLevel)
            .orElse(0);

        List<Map<String, Object>> result = new ArrayList<>();
        for (DefenseType type : DefenseType.values()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("defenseType", type.name());
            entry.put("metalCost", gameBalancer.getDefenseMetalCost(type));
            entry.put("crystalCost", gameBalancer.getDefenseCrystalCost(type));
            entry.put("gasCost", gameBalancer.getDefenseGasCost(type));
            entry.put("timeSeconds", gameBalancer.getDefenseBuildTimeSeconds(type, shipyardLevel, 0));
            entry.put("requiredShipyardLevel", gameBalancer.getRequiredShipyardLevelForDefense(type));
            entry.put("available", shipyardLevel >= gameBalancer.getRequiredShipyardLevelForDefense(type));
            result.add(entry);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PlanetDefense> getPlanetDefenses(Long planetId) {
        return planetDefenseRepository.findByPlanetId(planetId);
    }

    @Transactional(readOnly = true)
    public List<PlanetShip> getPlanetShips(Long planetId) {
        return planetShipRepository.findByPlanetId(planetId);
    }

    @Transactional
    public Map<String, Object> buildShips(Long planetId, ShipType shipType, int quantity, Long playerId) {
        var planet = planetRepository.findById(planetId)
            .orElseThrow(() -> new IllegalArgumentException("Planet not found: " + planetId));
        if (!planet.getPlayerId().equals(playerId)) {
            throw new IllegalArgumentException("Planet does not belong to player");
        }

        int shipyardLevel = buildingRepository
            .findByPlanetIdAndGridPosition(planetId, 9)
            .map(Building::getLevel)
            .orElse(0);
        if (shipyardLevel < gameBalancer.getRequiredShipyardLevel(shipType)) {
            throw new IllegalArgumentException("Shipyard level too low for " + shipType);
        }

        double metalCost = gameBalancer.getShipMetalCost(shipType) * quantity;
        double crystalCost = gameBalancer.getShipCrystalCost(shipType) * quantity;
        double gasCost = gameBalancer.getShipGasCost(shipType) * quantity;

        if (!economyService.checkAndDeduct(planetId, metalCost, crystalCost, gasCost)) {
            throw new IllegalArgumentException("Insufficient resources");
        }

        int timeSeconds = gameBalancer.getShipBuildTimeSeconds(shipType, shipyardLevel, 0);

        var queue = new ShipyardQueue();
        queue.setPlanetId(planetId);
        queue.setShipType(shipType);
        queue.setQuantity(quantity);
        queue.setBuiltQuantity(0);
        queue.setMetalCost(metalCost);
        queue.setCrystalCost(crystalCost);
        queue.setGasCost(gasCost);
        queue.setStartedAt(Instant.now());
        queue.setCompletesAt(Instant.now().plusSeconds(timeSeconds));
        shipyardQueueRepository.save(queue);

        Map<String, Object> result = new HashMap<>();
        result.put("queueId", queue.getId());
        result.put("shipType", shipType.name());
        result.put("quantity", quantity);
        result.put("completesAt", queue.getCompletesAt().toString());
        result.put("remainingSeconds", timeSeconds);
        return result;
    }

    @Transactional
    public Map<String, Object> buildDefense(Long planetId, DefenseType defenseType, int quantity, Long playerId) {
        var planet = planetRepository.findById(planetId)
            .orElseThrow(() -> new IllegalArgumentException("Planet not found: " + planetId));
        if (!planet.getPlayerId().equals(playerId)) {
            throw new IllegalArgumentException("Planet does not belong to player");
        }

        int shipyardLevel = buildingRepository
            .findByPlanetIdAndGridPosition(planetId, 9)
            .map(Building::getLevel)
            .orElse(0);
        if (shipyardLevel < gameBalancer.getRequiredShipyardLevelForDefense(defenseType)) {
            throw new IllegalArgumentException("Shipyard level too low for " + defenseType);
        }

        double metalCost = gameBalancer.getDefenseMetalCost(defenseType) * quantity;
        double crystalCost = gameBalancer.getDefenseCrystalCost(defenseType) * quantity;
        double gasCost = gameBalancer.getDefenseGasCost(defenseType) * quantity;

        if (!economyService.checkAndDeduct(planetId, metalCost, crystalCost, gasCost)) {
            throw new IllegalArgumentException("Insufficient resources");
        }

        int timeSeconds = gameBalancer.getDefenseBuildTimeSeconds(defenseType, shipyardLevel, 0);

        var queue = new ShipyardQueue();
        queue.setPlanetId(planetId);
        queue.setDefenseType(defenseType);
        queue.setQuantity(quantity);
        queue.setBuiltQuantity(0);
        queue.setMetalCost(metalCost);
        queue.setCrystalCost(crystalCost);
        queue.setGasCost(gasCost);
        queue.setStartedAt(Instant.now());
        queue.setCompletesAt(Instant.now().plusSeconds(timeSeconds));
        shipyardQueueRepository.save(queue);

        Map<String, Object> result = new HashMap<>();
        result.put("queueId", queue.getId());
        result.put("defenseType", defenseType.name());
        result.put("quantity", quantity);
        result.put("completesAt", queue.getCompletesAt().toString());
        result.put("remainingSeconds", timeSeconds);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ShipyardQueue> getShipyardQueue(Long planetId) {
        return shipyardQueueRepository.findByPlanetIdAndCompletedFalseOrderByStartedAt(planetId);
    }

    @Transactional
    public void completeShipyardEntry(Long queueId) {
        var queue = shipyardQueueRepository.findById(queueId)
            .orElseThrow(() -> new IllegalArgumentException("Shipyard queue not found: " + queueId));
        queue.setCompleted(true);
        queue.setBuiltQuantity(queue.getQuantity());
        shipyardQueueRepository.save(queue);

        if (queue.getDefenseType() != null) {
            var planetDefense = planetDefenseRepository
                .findByPlanetIdAndDefenseType(queue.getPlanetId(), queue.getDefenseType())
                .orElseGet(() -> planetDefenseRepository.save(new PlanetDefense(queue.getPlanetId(), queue.getDefenseType())));
            planetDefense.addQuantity(queue.getQuantity());
            planetDefenseRepository.save(planetDefense);
            return;
        }

        var planetShip = planetShipRepository
            .findByPlanetIdAndShipType(queue.getPlanetId(), queue.getShipType())
            .orElseGet(() -> planetShipRepository.save(new PlanetShip(queue.getPlanetId(), queue.getShipType())));
        planetShip.addQuantity(queue.getQuantity());
        planetShipRepository.save(planetShip);
    }

    @Transactional
    public void speedUpShipyardEntry(Long queueId, Long playerId) {
        ShipyardQueue queue = shipyardQueueRepository.findById(queueId)
            .orElseThrow(() -> new IllegalArgumentException("Queue item not found"));
        long remainingSeconds = Duration.between(Instant.now(), queue.getCompletesAt()).getSeconds();
        int cost = DarkMatterService.calculateSpeedUpCost(remainingSeconds);
        if (cost > 0 && !darkMatterService.spendDarkMatter(playerId, cost)) {
            throw new IllegalArgumentException("Not enough dark matter");
        }
        queue.setCompletesAt(Instant.now());
        shipyardQueueRepository.save(queue);
    }

    @Transactional(readOnly = true)
    public List<ShipyardQueue> getCompletedShipyardEntries(Instant before) {
        return shipyardQueueRepository.findByCompletedFalseAndCompletesAtLessThanEqual(before);
    }
}
