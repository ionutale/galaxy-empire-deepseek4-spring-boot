package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class BuildingService {

    private final PlanetRepository planetRepository;
    private final BuildingRepository buildingRepository;
    private final ConstructionQueueRepository constructionQueueRepository;
    private final GameBalancer balancer;
    private final PlanetService planetService;
    private final EconomyService economyService;
    private final DarkMatterService darkMatterService;
    private final int maxQueue;

    public BuildingService(PlanetRepository planetRepository, BuildingRepository buildingRepository,
                           ConstructionQueueRepository constructionQueueRepository,
                           GameBalancer balancer, PlanetService planetService,
                           EconomyService economyService,
                           DarkMatterService darkMatterService,
                           @Value("${game.constructions.max-queue-per-planet:5}") int maxQueue) {
        this.planetRepository = planetRepository;
        this.buildingRepository = buildingRepository;
        this.constructionQueueRepository = constructionQueueRepository;
        this.balancer = balancer;
        this.planetService = planetService;
        this.economyService = economyService;
        this.darkMatterService = darkMatterService;
        this.maxQueue = maxQueue;
    }

    @Transactional
    public Map<String, Object> queueUpgrade(Long planetId, int gridPosition, Long playerId) {
        Planet planet = planetRepository.findById(planetId)
            .orElseThrow(() -> new IllegalArgumentException("Planet not found"));

        if (!planet.getPlayerId().equals(playerId)) {
            throw new IllegalArgumentException("Not your planet");
        }

        long queueCount = constructionQueueRepository.countByPlanetIdAndCompletedFalse(planetId);
        if (queueCount >= maxQueue) {
            throw new IllegalArgumentException("Max queue reached (" + maxQueue + ")");
        }

        Building building = buildingRepository.findByPlanetIdAndGridPosition(planetId, gridPosition)
            .orElseThrow(() -> new IllegalArgumentException("No building at this position"));

        int targetLevel = building.getLevel() + 1;

        List<ConstructionQueue> existing = constructionQueueRepository
            .findByPlanetIdAndCompletedFalseOrderByStartedAt(planetId);
        boolean alreadyUpgrading = existing.stream()
            .anyMatch(q -> q.getBuildingType() == building.getBuildingType());
        if (alreadyUpgrading) {
            throw new IllegalArgumentException("This building is already being upgraded");
        }

        double metalCost = balancer.getMetalCost(building.getBuildingType(), targetLevel);
        double crystalCost = balancer.getCrystalCost(building.getBuildingType(), targetLevel);
        double gasCost = balancer.getGasCost(building.getBuildingType(), targetLevel);

        planetService.recalculate(planet);

        if (!economyService.checkAndDeduct(planetId, metalCost, crystalCost, gasCost)) {
            throw new IllegalArgumentException("Insufficient resources");
        }

        Building robotFactory = buildingRepository
            .findByPlanetIdAndBuildingType(planetId, BuildingType.ROBOT_FACTORY)
            .orElse(null);
        int rfLevel = robotFactory != null ? robotFactory.getLevel() : 0;

        int timeSeconds = balancer.getConstructionTimeSeconds(building.getBuildingType(), targetLevel, rfLevel);

        ConstructionQueue queue = new ConstructionQueue();
        queue.setPlanetId(planetId);
        queue.setBuildingType(building.getBuildingType());
        queue.setTargetLevel(targetLevel);
        queue.setMetalCost(metalCost);
        queue.setCrystalCost(crystalCost);
        queue.setGasCost(gasCost);
        queue.setStartedAt(Instant.now());
        queue.setCompletesAt(Instant.now().plusSeconds(timeSeconds));
        constructionQueueRepository.save(queue);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queueId", queue.getId());
        result.put("buildingType", building.getBuildingType());
        result.put("targetLevel", targetLevel);
        result.put("completesAt", queue.getCompletesAt().toString());
        result.put("remainingSeconds", timeSeconds);
        return result;
    }

    @Transactional
    public ConstructionQueue completeConstruction(Long queueId) {
        ConstructionQueue queue = constructionQueueRepository.findById(queueId)
            .orElseThrow(() -> new IllegalArgumentException("Queue entry not found"));

        if (queue.isCompleted()) {
            throw new IllegalArgumentException("Already completed");
        }

        Building building = buildingRepository.findByPlanetIdAndBuildingType(
            queue.getPlanetId(), queue.getBuildingType())
            .orElseThrow(() -> new IllegalArgumentException("Building not found"));

        building.setLevel(queue.getTargetLevel());
        buildingRepository.save(building);

        queue.setCompleted(true);
        return constructionQueueRepository.save(queue);
    }

    @Transactional
    public void speedUpConstruction(Long queueId, Long playerId) {
        ConstructionQueue queue = constructionQueueRepository.findById(queueId)
            .orElseThrow(() -> new IllegalArgumentException("Queue item not found"));
        long remainingSeconds = Duration.between(Instant.now(), queue.getCompletesAt()).getSeconds();
        int cost = DarkMatterService.calculateSpeedUpCost(remainingSeconds);
        if (cost > 0 && !darkMatterService.spendDarkMatter(playerId, cost)) {
            throw new IllegalArgumentException("Not enough dark matter");
        }
        queue.setCompletesAt(Instant.now());
        constructionQueueRepository.save(queue);
    }

    public Map<String, Object> getUpgradeCost(Long planetId, int gridPosition) {
        Building building = buildingRepository.findByPlanetIdAndGridPosition(planetId, gridPosition)
            .orElseThrow(() -> new IllegalArgumentException("No building at this position"));

        int targetLevel = building.getLevel() + 1;

        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("buildingType", building.getBuildingType());
        cost.put("currentLevel", building.getLevel());
        cost.put("targetLevel", targetLevel);
        cost.put("metal", balancer.getMetalCost(building.getBuildingType(), targetLevel));
        cost.put("crystal", balancer.getCrystalCost(building.getBuildingType(), targetLevel));
        cost.put("gas", balancer.getGasCost(building.getBuildingType(), targetLevel));
        cost.put("timeSeconds", balancer.getConstructionTimeSeconds(building.getBuildingType(), targetLevel, 0));
        return cost;
    }
}
