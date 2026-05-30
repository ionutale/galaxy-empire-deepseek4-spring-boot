package com.galaxyempire.game.web;

import com.galaxyempire.game.domain.DebrisField;
import com.galaxyempire.game.domain.FleetMission;
import com.galaxyempire.game.domain.Planet;
import com.galaxyempire.game.service.FleetService;
import com.galaxyempire.game.service.PlanetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/game")
public class FleetController {

    private final FleetService fleetService;
    private final PlanetService planetService;

    public FleetController(FleetService fleetService, PlanetService planetService) {
        this.fleetService = fleetService;
        this.planetService = planetService;
    }

    @PostMapping("/planets/{planetId}/fleet")
    public ResponseEntity<?> launchFleet(@PathVariable Long planetId,
                                           @RequestBody Map<String, Object> body,
                                           @RequestHeader("X-Player-Id") Long playerId) {
        try {
            FleetMission mission = FleetMission.valueOf(body.get("mission").toString().toUpperCase());
            Map<String, Integer> ships = (Map<String, Integer>) body.get("ships");

            if (mission == FleetMission.COLONIZE) {
                int galaxy = Integer.parseInt(body.get("galaxy").toString());
                int systemId = Integer.parseInt(body.get("systemId").toString());
                int slot = Integer.parseInt(body.get("slot").toString());
                Planet planet = planetService.createPlanetAt(playerId, galaxy, systemId, slot);
                var result = fleetService.launchFleet(planetId, planet.getId(), mission, ships, playerId, body);
                return ResponseEntity.ok(result);
            }

            Long targetPlanetId = Long.valueOf(body.get("targetPlanetId").toString());
            var result = fleetService.launchFleet(planetId, targetPlanetId, mission, ships, playerId, body);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/planets/{planetId}/fleet")
    public ResponseEntity<?> getPlanetFleets(@PathVariable Long planetId) {
        return ResponseEntity.ok(fleetService.getPlanetFleets(planetId));
    }

    @GetMapping("/fleet/{fleetId}")
    public ResponseEntity<?> getFleetDetail(@PathVariable Long fleetId) {
        try {
            return ResponseEntity.ok(fleetService.getFleetDetail(fleetId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fleet/{fleetId}/recall")
    public ResponseEntity<?> recallFleet(@PathVariable Long fleetId,
                                          @RequestHeader("X-Player-Id") Long playerId) {
        try {
            fleetService.recallFleet(fleetId, playerId);
            return ResponseEntity.ok(Map.of("status", "recalled"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/planets/{planetId}/combat-reports")
    public ResponseEntity<?> getCombatReports(@PathVariable Long planetId) {
        return ResponseEntity.ok(fleetService.getPlanetCombatReports(planetId));
    }

    @GetMapping("/combat-reports/{reportId}")
    public ResponseEntity<?> getCombatReport(@PathVariable Long reportId) {
        try {
            return ResponseEntity.ok(fleetService.getCombatReport(reportId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/planets/{planetId}/debris")
    public ResponseEntity<?> getDebrisField(@PathVariable Long planetId) {
        Optional<DebrisField> df = fleetService.getDebrisField(planetId);
        if (df.isPresent()) {
            return ResponseEntity.ok(df.get());
        }
        return ResponseEntity.ok(Map.of("metal", 0, "crystal", 0));
    }

    @GetMapping("/planets/{planetId}/espionage-reports")
    public ResponseEntity<?> getEspionageReports(@PathVariable Long planetId) {
        return ResponseEntity.ok(fleetService.getPlanetEspionageReports(planetId));
    }
}
