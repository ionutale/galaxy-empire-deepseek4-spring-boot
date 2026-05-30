package com.galaxyempire.game.web;

import com.galaxyempire.game.domain.DefenseType;
import com.galaxyempire.game.domain.ShipType;
import com.galaxyempire.game.service.ShipyardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class ShipyardController {

    private final ShipyardService shipyardService;

    public ShipyardController(ShipyardService shipyardService) {
        this.shipyardService = shipyardService;
    }

    @GetMapping("/planets/{planetId}/ships")
    public ResponseEntity<?> getPlanetShips(@PathVariable Long planetId) {
        return ResponseEntity.ok(shipyardService.getPlanetShips(planetId));
    }

    @GetMapping("/planets/{planetId}/shipyard")
    public ResponseEntity<?> getAvailableShips(@PathVariable Long planetId) {
        return ResponseEntity.ok(shipyardService.getShipTypes(planetId));
    }

    @PostMapping("/planets/{planetId}/ships/{type}/build")
    public ResponseEntity<?> buildShips(@PathVariable Long planetId,
                                         @PathVariable String type,
                                         @RequestBody Map<String, Integer> body,
                                         @RequestHeader("X-Player-Id") Long playerId) {
        try {
            ShipType shipType = ShipType.valueOf(type.toUpperCase());
            int quantity = body.getOrDefault("quantity", 1);
            var result = shipyardService.buildShips(planetId, shipType, quantity, playerId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/planets/{planetId}/shipyard-queue")
    public ResponseEntity<?> getShipyardQueue(@PathVariable Long planetId) {
        return ResponseEntity.ok(shipyardService.getShipyardQueue(planetId));
    }

    @GetMapping("/planets/{planetId}/defense-types")
    public ResponseEntity<?> getDefenseTypes(@PathVariable Long planetId) {
        return ResponseEntity.ok(shipyardService.getDefenseTypes(planetId));
    }

    @PostMapping("/planets/{planetId}/defense")
    public ResponseEntity<?> buildDefense(@PathVariable Long planetId,
                                           @RequestBody Map<String, Object> body,
                                           @RequestHeader("X-Player-Id") Long playerId) {
        try {
            DefenseType type = DefenseType.valueOf(body.get("defenseType").toString().toUpperCase());
            int quantity = Integer.parseInt(body.get("quantity").toString());
            var result = shipyardService.buildDefense(planetId, type, quantity, playerId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/planets/{planetId}/defenses")
    public ResponseEntity<?> getPlanetDefenses(@PathVariable Long planetId) {
        return ResponseEntity.ok(shipyardService.getPlanetDefenses(planetId));
    }
}
