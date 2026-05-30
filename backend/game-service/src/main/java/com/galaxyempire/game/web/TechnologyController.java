package com.galaxyempire.game.web;

import com.galaxyempire.game.domain.Technology;
import com.galaxyempire.game.service.DarkMatterService;
import com.galaxyempire.game.service.ResearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class TechnologyController {

    private final ResearchService researchService;
    private final DarkMatterService darkMatterService;

    public TechnologyController(ResearchService researchService, DarkMatterService darkMatterService) {
        this.researchService = researchService;
        this.darkMatterService = darkMatterService;
    }

    @GetMapping("/technologies")
    public ResponseEntity<?> getTechnologies(@RequestHeader("X-Player-Id") Long playerId) {
        return ResponseEntity.ok(researchService.getTechnologies(playerId));
    }

    @GetMapping("/technologies/{name}")
    public ResponseEntity<?> getTechnology(@PathVariable String name,
                                           @RequestHeader("X-Player-Id") Long playerId) {
        try {
            Technology tech = Technology.valueOf(name.toUpperCase());
            return ResponseEntity.ok(researchService.getTechnologyDetails(playerId, tech));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown technology: " + name));
        }
    }

    @PostMapping("/technologies/{name}/research")
    public ResponseEntity<?> startResearch(@PathVariable String name,
                                            @RequestHeader("X-Player-Id") Long playerId) {
        try {
            Technology tech = Technology.valueOf(name.toUpperCase());
            var result = researchService.startResearch(playerId, tech);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/research-queue")
    public ResponseEntity<?> getResearchQueue(@RequestHeader("X-Player-Id") Long playerId) {
        return ResponseEntity.ok(researchService.getActiveResearch(playerId));
    }

    @PostMapping("/technologies/speed-up")
    public ResponseEntity<?> speedUpResearch(@RequestBody Map<String, String> body,
                                              @RequestHeader("X-Player-Id") Long playerId) {
        try {
            String technology = body.get("technology");
            researchService.speedUpResearch(playerId, technology);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
