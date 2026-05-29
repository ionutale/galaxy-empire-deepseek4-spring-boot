package com.galaxyempire.game.web;

import com.galaxyempire.game.service.PlanetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class PlanetController {

    private final PlanetService planetService;

    public PlanetController(PlanetService planetService) {
        this.planetService = planetService;
    }

    @GetMapping("/planets/{id}")
    public ResponseEntity<?> getPlanet(@PathVariable Long id,
                                        @RequestHeader("X-Player-Id") Long playerId) {
        try {
            Map<String, Object> details = planetService.getPlanetDetails(id);
            return ResponseEntity.ok(details);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/planets")
    public ResponseEntity<?> createPlanet(@RequestHeader("X-Player-Id") Long playerId) {
        try {
            var planet = planetService.createStarterPlanet(playerId);
            return ResponseEntity.ok(Map.of(
                "id", planet.getId(),
                "name", planet.getName(),
                "coordinates", planet.getGalaxy() + ":" + planet.getSystemId() + ":" + planet.getSlot()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/planets/my")
    public ResponseEntity<?> getMyPlanets(@RequestHeader("X-Player-Id") Long playerId) {
        var planets = planetService.getPlanetsByPlayer(playerId);
        return ResponseEntity.ok(planets);
    }
}
