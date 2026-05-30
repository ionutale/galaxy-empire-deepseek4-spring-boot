package com.galaxyempire.game.web;

import com.galaxyempire.game.service.BuildingService;
import com.galaxyempire.game.service.DarkMatterService;
import com.galaxyempire.game.service.EconomyService;
import com.galaxyempire.game.service.PlanetService;
import com.galaxyempire.game.service.ShipyardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class PlanetController {

    private final PlanetService planetService;
    private final EconomyService economyService;
    private final DarkMatterService darkMatterService;
    private final BuildingService buildingService;
    private final ShipyardService shipyardService;

    public PlanetController(PlanetService planetService, EconomyService economyService,
                            DarkMatterService darkMatterService, BuildingService buildingService,
                            ShipyardService shipyardService) {
        this.planetService = planetService;
        this.economyService = economyService;
        this.darkMatterService = darkMatterService;
        this.buildingService = buildingService;
        this.shipyardService = shipyardService;
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

    @GetMapping("/planets/{id}/resources")
    public ResponseEntity<?> getPlanetResources(@PathVariable Long id) {
        return ResponseEntity.ok(economyService.getCurrentResources(id));
    }

    @GetMapping("/players/{playerId}/dark-matter")
    public ResponseEntity<?> getDarkMatter(@PathVariable Long playerId) {
        return ResponseEntity.ok(Map.of("darkMatter", darkMatterService.getDarkMatter(playerId)));
    }

    @PostMapping("/players/{playerId}/dark-matter/add")
    public ResponseEntity<?> addDarkMatter(@PathVariable Long playerId, @RequestBody Map<String, Integer> body) {
        darkMatterService.addDarkMatter(playerId, body.getOrDefault("amount", 0));
        return ResponseEntity.ok(Map.of("darkMatter", darkMatterService.getDarkMatter(playerId)));
    }

    @PostMapping("/planets/{planetId}/buildings/queue/{queueId}/speed-up")
    public ResponseEntity<?> speedUpBuilding(@PathVariable Long planetId, @PathVariable Long queueId,
                                              @RequestHeader("X-Player-Id") Long playerId) {
        try {
            buildingService.speedUpConstruction(queueId, playerId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/planets/{planetId}/shipyard/{queueId}/speed-up")
    public ResponseEntity<?> speedUpShipyard(@PathVariable Long planetId, @PathVariable Long queueId,
                                              @RequestHeader("X-Player-Id") Long playerId) {
        try {
            shipyardService.speedUpShipyardEntry(queueId, playerId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
