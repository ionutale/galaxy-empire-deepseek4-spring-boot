package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ResearchService {

    private final PlayerTechnologyRepository playerTechnologyRepository;
    private final ResearchQueueRepository researchQueueRepository;
    private final BuildingRepository buildingRepository;
    private final GameBalancer gameBalancer;
    private final DarkMatterService darkMatterService;

    public ResearchService(PlayerTechnologyRepository playerTechnologyRepository,
                           ResearchQueueRepository researchQueueRepository,
                           BuildingRepository buildingRepository,
                           GameBalancer gameBalancer,
                           DarkMatterService darkMatterService) {
        this.playerTechnologyRepository = playerTechnologyRepository;
        this.researchQueueRepository = researchQueueRepository;
        this.buildingRepository = buildingRepository;
        this.gameBalancer = gameBalancer;
        this.darkMatterService = darkMatterService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTechnologies(Long playerId) {
        var playerTechs = playerTechnologyRepository.findByPlayerId(playerId);
        var techLevelMap = playerTechs.stream()
            .collect(Collectors.toMap(PlayerTechnology::getTechnology, PlayerTechnology::getLevel));
        var activeQueue = researchQueueRepository.findByPlayerIdAndCompletedFalse(playerId);
        var activeTech = activeQueue.isEmpty() ? null : activeQueue.get(0).getTechnology();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Technology tech : Technology.values()) {
            int currentLevel = techLevelMap.getOrDefault(tech, 0);
            Map<String, Object> entry = new HashMap<>();
            entry.put("technology", tech.name());
            entry.put("level", currentLevel);
            entry.put("metalCost", gameBalancer.getTechnologyMetalCost(tech, currentLevel));
            entry.put("crystalCost", gameBalancer.getTechnologyCrystalCost(tech, currentLevel));
            entry.put("gasCost", gameBalancer.getTechnologyGasCost(tech, currentLevel));
            entry.put("timeSeconds", gameBalancer.getResearchTimeSeconds(tech, currentLevel,
                getHighestResearchLabLevel(playerId)));
            entry.put("prerequisitesMet", gameBalancer.meetsPrerequisites(tech, techLevelMap));
            entry.put("isResearching", tech == activeTech);
            result.add(entry);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTechnologyDetails(Long playerId, Technology tech) {
        var playerTech = playerTechnologyRepository.findByPlayerIdAndTechnology(playerId, tech);
        int currentLevel = playerTech.map(PlayerTechnology::getLevel).orElse(0);
        var techLevelMap = playerTechnologyRepository.findByPlayerId(playerId).stream()
            .collect(Collectors.toMap(PlayerTechnology::getTechnology, PlayerTechnology::getLevel));

        Map<String, Object> result = new HashMap<>();
        result.put("technology", tech.name());
        result.put("level", currentLevel);
        result.put("metalCost", gameBalancer.getTechnologyMetalCost(tech, currentLevel));
        result.put("crystalCost", gameBalancer.getTechnologyCrystalCost(tech, currentLevel));
        result.put("gasCost", gameBalancer.getTechnologyGasCost(tech, currentLevel));
        result.put("timeSeconds", gameBalancer.getResearchTimeSeconds(tech, currentLevel,
            getHighestResearchLabLevel(playerId)));
        result.put("prerequisitesMet", gameBalancer.meetsPrerequisites(tech, techLevelMap));
        return result;
    }

    @Transactional
    public Map<String, Object> startResearch(Long playerId, Technology tech) {
        if (researchQueueRepository.existsByPlayerIdAndCompletedFalse(playerId)) {
            throw new IllegalArgumentException("Already researching a technology");
        }
        var playerTech = playerTechnologyRepository
            .findByPlayerIdAndTechnology(playerId, tech)
            .orElseGet(() -> playerTechnologyRepository.save(new PlayerTechnology(playerId, tech)));

        int currentLevel = playerTech.getLevel();
        var techLevelMap = playerTechnologyRepository.findByPlayerId(playerId).stream()
            .collect(Collectors.toMap(PlayerTechnology::getTechnology, PlayerTechnology::getLevel));

        if (!gameBalancer.meetsPrerequisites(tech, techLevelMap)) {
            throw new IllegalArgumentException("Prerequisites not met for " + tech);
        }

        double metalCost = gameBalancer.getTechnologyMetalCost(tech, currentLevel);
        double crystalCost = gameBalancer.getTechnologyCrystalCost(tech, currentLevel);
        double gasCost = gameBalancer.getTechnologyGasCost(tech, currentLevel);
        int timeSeconds = gameBalancer.getResearchTimeSeconds(tech, currentLevel,
            getHighestResearchLabLevel(playerId));

        var queue = new ResearchQueue();
        queue.setPlayerId(playerId);
        queue.setTechnology(tech);
        queue.setTargetLevel(currentLevel + 1);
        queue.setMetalCost(metalCost);
        queue.setCrystalCost(crystalCost);
        queue.setGasCost(gasCost);
        queue.setStartedAt(Instant.now());
        queue.setCompletesAt(Instant.now().plusSeconds(timeSeconds));
        researchQueueRepository.save(queue);

        Map<String, Object> result = new HashMap<>();
        result.put("queueId", queue.getId());
        result.put("technology", tech.name());
        result.put("targetLevel", currentLevel + 1);
        result.put("completesAt", queue.getCompletesAt().toString());
        result.put("remainingSeconds", timeSeconds);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ResearchQueue> getActiveResearch(Long playerId) {
        return researchQueueRepository.findByPlayerIdAndCompletedFalse(playerId);
    }

    @Transactional
    public void completeResearch(Long queueId) {
        var queue = researchQueueRepository.findById(queueId)
            .orElseThrow(() -> new IllegalArgumentException("Research queue not found: " + queueId));
        queue.setCompleted(true);
        researchQueueRepository.save(queue);

        var playerTech = playerTechnologyRepository
            .findByPlayerIdAndTechnology(queue.getPlayerId(), queue.getTechnology())
            .orElseThrow();
        playerTech.setLevel(queue.getTargetLevel());
        playerTechnologyRepository.save(playerTech);
    }

    @Transactional
    public void speedUpResearch(Long playerId, String technology) {
        ResearchQueue queue = researchQueueRepository
            .findByPlayerIdAndCompletedFalseAndTechnology(playerId, Technology.valueOf(technology))
            .orElseThrow(() -> new IllegalArgumentException("No active research for " + technology));
        long remainingSeconds = Duration.between(Instant.now(), queue.getCompletesAt()).getSeconds();
        int cost = DarkMatterService.calculateSpeedUpCost(remainingSeconds);
        if (cost > 0 && !darkMatterService.spendDarkMatter(playerId, cost)) {
            throw new IllegalArgumentException("Not enough dark matter");
        }
        queue.setCompletesAt(Instant.now());
        researchQueueRepository.save(queue);
    }

    @Transactional(readOnly = true)
    public List<ResearchQueue> getCompletedResearches(Instant before) {
        return researchQueueRepository.findByCompletedFalseAndCompletesAtLessThanEqual(before);
    }

    private double getHighestResearchLabLevel(Long playerId) {
        var researchLabs = buildingRepository.findByPlayerId(playerId)
            .stream()
            .filter(b -> b.getBuildingType() == BuildingType.RESEARCH_LAB)
            .mapToInt(Building::getLevel)
            .max();
        return researchLabs.orElse(0);
    }
}
