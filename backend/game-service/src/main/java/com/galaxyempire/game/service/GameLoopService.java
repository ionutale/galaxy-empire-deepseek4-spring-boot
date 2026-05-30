package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.ConstructionQueue;
import com.galaxyempire.game.repository.ConstructionQueueRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class GameLoopService {

    private final ConstructionQueueRepository constructionQueueRepository;
    private final BuildingService buildingService;
    private final ResearchService researchService;
    private final ShipyardService shipyardService;
    private final FleetService fleetService;
    private final EconomyService economyService;
    private final SimpMessagingTemplate messagingTemplate;

    public GameLoopService(ConstructionQueueRepository constructionQueueRepository,
                           BuildingService buildingService,
                           ResearchService researchService,
                           ShipyardService shipyardService,
                           FleetService fleetService,
                           EconomyService economyService,
                           SimpMessagingTemplate messagingTemplate) {
        this.constructionQueueRepository = constructionQueueRepository;
        this.buildingService = buildingService;
        this.researchService = researchService;
        this.shipyardService = shipyardService;
        this.fleetService = fleetService;
        this.economyService = economyService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void processGameLoop() {
        List<ConstructionQueue> dueConstructions = constructionQueueRepository
            .findByCompletedFalseAndCompletesAtLessThanEqual(Instant.now());

        for (ConstructionQueue queue : dueConstructions) {
            try {
                ConstructionQueue completed = buildingService.completeConstruction(queue.getId());
                Long planetId = completed.getPlanetId();
                messagingTemplate.convertAndSend(
                    "/topic/planet/" + planetId,
                    Map.of("type", "CONSTRUCTION_COMPLETE",
                           "buildingType", completed.getBuildingType(),
                           "newLevel", completed.getTargetLevel())
                );
            } catch (Exception e) {
                System.err.println("Failed to process construction " + queue.getId() + ": " + e.getMessage());
            }
        }

        processResearchCompletions();
        processShipyardCompletions();

        fleetService.processArrivals(Instant.now());
        fleetService.processReturns(Instant.now());
    }

    private void processResearchCompletions() {
        var completed = researchService.getCompletedResearches(Instant.now());
        for (var queue : completed) {
            try {
                researchService.completeResearch(queue.getId());
                messagingTemplate.convertAndSend(
                    "/topic/research/" + queue.getPlayerId(),
                    Map.of("type", "RESEARCH_COMPLETE",
                           "technology", queue.getTechnology().name(),
                           "level", queue.getTargetLevel())
                );
            } catch (Exception e) {
                System.err.println("Failed to process research " + queue.getId() + ": " + e.getMessage());
            }
        }
    }

    @Scheduled(fixedRate = 10000)
    @Transactional
    public void processResourceTick() {
        economyService.tickResources();
    }

    private void processShipyardCompletions() {
        var completed = shipyardService.getCompletedShipyardEntries(Instant.now());
        for (var queue : completed) {
            try {
                shipyardService.completeShipyardEntry(queue.getId());
                messagingTemplate.convertAndSend(
                    "/topic/planet/" + queue.getPlanetId(),
                    Map.of("type", "SHIP_BUILD_COMPLETE",
                           "shipType", queue.getShipType().name(),
                           "quantity", queue.getQuantity())
                );
            } catch (Exception e) {
                System.err.println("Failed to process shipyard " + queue.getId() + ": " + e.getMessage());
            }
        }
    }
}
