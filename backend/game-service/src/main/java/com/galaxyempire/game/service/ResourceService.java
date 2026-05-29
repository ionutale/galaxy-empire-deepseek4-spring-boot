package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.Planet;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

@Service
public class ResourceService {

    private final GameBalancer balancer;

    public ResourceService(GameBalancer balancer) {
        this.balancer = balancer;
    }

    public void recalculateResources(Planet planet, double metalProd, double crystalProd, double gasProd) {
        Instant now = Instant.now();
        double hoursElapsed = Duration.between(planet.getLastUpdated(), now).toMillis() / 3600000.0;
        if (hoursElapsed > 0) {
            planet.setMetal(planet.getMetal() + metalProd * hoursElapsed);
            planet.setCrystal(planet.getCrystal() + crystalProd * hoursElapsed);
            planet.setGas(planet.getGas() + gasProd * hoursElapsed);
        }
        planet.setLastUpdated(now);
    }
}
