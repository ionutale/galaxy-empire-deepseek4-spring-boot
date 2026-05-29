package com.galaxyempire.game.web;

import com.galaxyempire.game.repository.ConstructionQueueRepository;
import com.galaxyempire.game.service.BuildingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class BuildingController {

    private final BuildingService buildingService;
    private final ConstructionQueueRepository constructionQueueRepository;

    public BuildingController(BuildingService buildingService,
                              ConstructionQueueRepository constructionQueueRepository) {
        this.buildingService = buildingService;
        this.constructionQueueRepository = constructionQueueRepository;
    }

    @PostMapping("/planets/{planetId}/buildings/{gridPosition}/upgrade")
    public ResponseEntity<?> upgradeBuilding(@PathVariable Long planetId,
                                              @PathVariable int gridPosition,
                                              @RequestHeader("X-Player-Id") Long playerId) {
        try {
            var result = buildingService.queueUpgrade(planetId, gridPosition, playerId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/planets/{planetId}/buildings/{gridPosition}/cost")
    public ResponseEntity<?> getUpgradeCost(@PathVariable Long planetId,
                                             @PathVariable int gridPosition) {
        try {
            var cost = buildingService.getUpgradeCost(planetId, gridPosition);
            return ResponseEntity.ok(cost);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/planets/{planetId}/queue")
    public ResponseEntity<?> getQueue(@PathVariable Long planetId) {
        var queue = constructionQueueRepository
            .findByPlanetIdAndCompletedFalseOrderByStartedAt(planetId);
        return ResponseEntity.ok(queue);
    }
}
