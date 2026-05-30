package com.galaxyempire.game.web;

import com.galaxyempire.game.service.GalaxyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GalaxyController {

    private final GalaxyService galaxyService;

    public GalaxyController(GalaxyService galaxyService) {
        this.galaxyService = galaxyService;
    }

    @GetMapping("/galaxies/{galaxy}/systems")
    public ResponseEntity<?> getSystemList(@PathVariable int galaxy,
                                            @RequestHeader("X-Player-Id") Long playerId) {
        return ResponseEntity.ok(galaxyService.getSystemList(galaxy, playerId));
    }

    @GetMapping("/galaxies/{galaxy}/systems/{systemId}")
    public ResponseEntity<?> getSystemDetail(@PathVariable int galaxy,
                                              @PathVariable int systemId,
                                              @RequestHeader("X-Player-Id") Long playerId) {
        return ResponseEntity.ok(galaxyService.getSystemDetail(galaxy, systemId, playerId));
    }
}
