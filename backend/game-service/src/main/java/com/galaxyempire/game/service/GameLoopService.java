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
    private final SimpMessagingTemplate messagingTemplate;

    public GameLoopService(ConstructionQueueRepository constructionQueueRepository,
                           BuildingService buildingService,
                           SimpMessagingTemplate messagingTemplate) {
        this.constructionQueueRepository = constructionQueueRepository;
        this.buildingService = buildingService;
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
    }
}
