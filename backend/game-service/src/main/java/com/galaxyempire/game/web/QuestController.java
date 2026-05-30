package com.galaxyempire.game.web;

import com.galaxyempire.game.service.QuestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class QuestController {

    private final QuestService questService;

    public QuestController(QuestService questService) {
        this.questService = questService;
    }

    @GetMapping("/quests")
    public ResponseEntity<?> getQuests(@RequestHeader("X-Player-Id") Long playerId) {
        return ResponseEntity.ok(questService.getAvailableQuests(playerId));
    }

    @PostMapping("/quests/{progressId}/claim")
    public ResponseEntity<?> claimReward(@PathVariable Long progressId,
                                          @RequestHeader("X-Player-Id") Long playerId) {
        try {
            return ResponseEntity.ok(questService.claimReward(playerId, progressId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
