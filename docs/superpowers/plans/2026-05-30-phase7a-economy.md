# Phase 7a: Core Economy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add centralized resource management, storage caps, fusion reactor, energy deficit, recurring production ticks, and frontend resource header bar.

**Architecture:** New EconomyService owns all resource operations (check/deduct/refund/tick). GameBalancer gets fusion formulas. PlanetService.recalculate() enhanced with fusion + deficit. GameLoop calls tickResources() every 10s. Frontend gets ResourceBarComponent + PlanetStateService for active planet.

**Tech Stack:** Spring Boot 3.4, Java 21, PostgreSQL 16, Angular 19

---

## File Structure

### Backend — Create:
- `backend/game-service/src/main/java/com/galaxyempire/game/service/EconomyService.java`

### Backend — Modify:
- `backend/game-service/src/main/java/com/galaxyempire/game/service/GameBalancer.java` (fusion formulas)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/PlanetService.java` (recalculate with energy deficit + fusion)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/BuildingService.java` (use EconomyService)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/ShipyardService.java` (use EconomyService)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/ResearchService.java` (use EconomyService)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/FleetService.java` (use EconomyService)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/GameLoopService.java` (tickResources call)
- `backend/game-service/src/main/java/com/galaxyempire/game/web/PlanetController.java` (resources endpoint)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/ResourceService.java` (merge into EconomyService)

### Frontend — Create:
- `frontend/src/app/core/services/planet-state.service.ts`
- `frontend/src/app/resource-bar/resource-bar.component.ts`

### Frontend — Modify:
- `frontend/src/app/core/models/models.ts` (add PlanetResourcesResponse)
- `frontend/src/app/core/services/game.service.ts` (add getPlanetResources)
- `frontend/src/app/app.component.ts` (add resource bar)
- `frontend/src/app/overview/overview.component.ts` (remove inline resources)
- `frontend/src/app/resources/resources.component.ts` (remove inline header)

---

### Task 1: Fusion formulas in GameBalancer

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/GameBalancer.java`

- [ ] **Add fusion energy and gas consumption formulas**

After the `getSolarPlantEnergy()` method, add:

```java
    public double getFusionEnergy(int level, int energyTechLevel) {
        double baseMultiplier = 1.05 + 0.01 * energyTechLevel;
        return 30 * level * Math.pow(baseMultiplier, level);
    }

    public double getFusionGasCost(int level) {
        return 10 * level * Math.pow(1.1, level);
    }
```

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Expected: BUILD SUCCESS

---

### Task 2: PlanetService — recalculate with energy deficit + fusion

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/PlanetService.java`

- [ ] **Read PlanetService.java** to understand current `recalculate()` and `getDetails()` methods

- [ ] **Enhance `recalculate()` to compute fusion energy and energy deficit**

After building iteration that sums mine energy consumption and solar plant energy, add:

```java
            // Fusion reactor
            int fusionLevel = buildings.stream()
                .filter(b -> b.getBuildingType() == BuildingType.FUSION_REACTOR)
                .mapToInt(Building::getLevel)
                .findFirst().orElse(0);
            int energyTechLevel = playerTechnologyRepository
                .findByPlayerIdAndTechnology(planet.getPlayerId(), Technology.ENERGY_TECHNOLOGY)
                .map(PlayerTechnology::getLevel)
                .orElse(0);
            double fusionEnergy = 0;
            double fusionCost = 0;
            if (fusionLevel > 0) {
                fusionEnergy = gameBalancer.getFusionEnergy(fusionLevel, energyTechLevel);
                fusionCost = gameBalancer.getFusionGasCost(fusionLevel);
            }
            double totalEnergy = solarEnergy + fusionEnergy;
```

And ensure the returned map includes `totalEnergy`, `energyConsumption` (total mine consumption), `fusionEnergy`, `fusionCost`, and `netEnergy = totalEnergy - totalConsumption`.

- [ ] **Verify existing tests pass**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am test -DskipTests=false 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

### Task 3: EconomyService

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/service/EconomyService.java`

- [ ] **Create EconomyService**

```java
package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class EconomyService {

    private final PlanetRepository planetRepository;
    private final BuildingRepository buildingRepository;
    private final GameBalancer gameBalancer;
    private final PlayerTechnologyRepository playerTechnologyRepository;

    public EconomyService(PlanetRepository planetRepository,
                          BuildingRepository buildingRepository,
                          GameBalancer gameBalancer,
                          PlayerTechnologyRepository playerTechnologyRepository) {
        this.planetRepository = planetRepository;
        this.buildingRepository = buildingRepository;
        this.gameBalancer = gameBalancer;
        this.playerTechnologyRepository = playerTechnologyRepository;
    }

    @Transactional
    public boolean checkAndDeduct(Long planetId, double metal, double crystal, double gas) {
        Planet planet = planetRepository.findById(planetId).orElseThrow();
        if (planet.getMetal() < metal || planet.getCrystal() < crystal || planet.getGas() < gas) {
            return false;
        }
        planet.setMetal(planet.getMetal() - metal);
        planet.setCrystal(planet.getCrystal() - crystal);
        planet.setGas(planet.getGas() - gas);
        planetRepository.save(planet);
        return true;
    }

    @Transactional
    public void refund(Long planetId, double metal, double crystal, double gas) {
        Planet planet = planetRepository.findById(planetId).orElseThrow();
        Map<String, Double> caps = getStorageCaps(planet);
        planet.setMetal(Math.min(planet.getMetal() + metal, caps.get("metalStorage")));
        planet.setCrystal(Math.min(planet.getCrystal() + crystal, caps.get("crystalStorage")));
        planet.setGas(Math.min(planet.getGas() + gas, caps.get("gasStorage")));
        planetRepository.save(planet);
    }

    @Transactional
    public void addResources(Long planetId, double metal, double crystal, double gas) {
        Planet planet = planetRepository.findById(planetId).orElseThrow();
        Map<String, Double> caps = getStorageCaps(planet);
        planet.setMetal(Math.min(planet.getMetal() + metal, caps.get("metalStorage")));
        planet.setCrystal(Math.min(planet.getCrystal() + crystal, caps.get("crystalStorage")));
        planet.setGas(Math.min(planet.getGas() + gas, caps.get("gasStorage")));
        planetRepository.save(planet);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentResources(Long planetId) {
        Planet planet = planetRepository.findById(planetId).orElseThrow();
        Map<String, Double> rates = getProductionRates(planet);
        Map<String, Double> caps = getStorageCaps(planet);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planetId", planetId);
        result.put("metal", planet.getMetal());
        result.put("crystal", planet.getCrystal());
        result.put("gas", planet.getGas());
        result.put("energy", rates.get("netEnergy"));
        result.put("metalRate", rates.get("metalRate"));
        result.put("crystalRate", rates.get("crystalRate"));
        result.put("gasRate", rates.get("gasRate"));
        result.put("metalStorage", caps.get("metalStorage"));
        result.put("crystalStorage", caps.get("crystalStorage"));
        result.put("gasStorage", caps.get("gasStorage"));
        result.put("energyConsumption", rates.get("energyConsumption"));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Double> getStorageCaps(Planet planet) {
        List<Building> buildings = buildingRepository.findByPlanetId(planet.getId());
        int metalStorageLevel = buildings.stream()
            .filter(b -> b.getBuildingType() == BuildingType.METAL_STORAGE)
            .mapToInt(Building::getLevel)
            .findFirst().orElse(0);
        int crystalStorageLevel = buildings.stream()
            .filter(b -> b.getBuildingType() == BuildingType.CRYSTAL_STORAGE)
            .mapToInt(Building::getLevel)
            .findFirst().orElse(0);
        int gasStorageLevel = buildings.stream()
            .filter(b -> b.getBuildingType() == BuildingType.GAS_STORAGE)
            .mapToInt(Building::getLevel)
            .findFirst().orElse(0);
        Map<String, Double> caps = new HashMap<>();
        caps.put("metalStorage", gameBalancer.getStorageCapacity(metalStorageLevel));
        caps.put("crystalStorage", gameBalancer.getStorageCapacity(crystalStorageLevel));
        caps.put("gasStorage", gameBalancer.getStorageCapacity(gasStorageLevel));
        return caps;
    }

    @Transactional(readOnly = true)
    public Map<String, Double> getProductionRates(Planet planet) {
        List<Building> buildings = buildingRepository.findByPlanetId(planet.getId());
        int metalMineLevel = buildings.stream().filter(b -> b.getBuildingType() == BuildingType.METAL_MINE).mapToInt(Building::getLevel).findFirst().orElse(0);
        int crystalMineLevel = buildings.stream().filter(b -> b.getBuildingType() == BuildingType.CRYSTAL_MINE).mapToInt(Building::getLevel).findFirst().orElse(0);
        int gasMineLevel = buildings.stream().filter(b -> b.getBuildingType() == BuildingType.GAS_MINE).mapToInt(Building::getLevel).findFirst().orElse(0);
        int solarPlantLevel = buildings.stream().filter(b -> b.getBuildingType() == BuildingType.SOLAR_PLANT).mapToInt(Building::getLevel).findFirst().orElse(0);
        int fusionLevel = buildings.stream().filter(b -> b.getBuildingType() == BuildingType.FUSION_REACTOR).mapToInt(Building::getLevel).findFirst().orElse(0);

        double solarEnergy = gameBalancer.getSolarPlantEnergy(solarPlantLevel);
        double mineConsumption = gameBalancer.getMineEnergyConsumption(metalMineLevel)
            + gameBalancer.getMineEnergyConsumption(crystalMineLevel)
            + gameBalancer.getMineEnergyConsumption(gasMineLevel);

        int energyTechLevel = playerTechnologyRepository
            .findByPlayerIdAndTechnology(planet.getPlayerId(), Technology.ENERGY_TECHNOLOGY)
            .map(PlayerTechnology::getLevel)
            .orElse(0);

        double fusionEnergy = 0;
        double fusionCost = 0;
        if (fusionLevel > 0) {
            fusionEnergy = gameBalancer.getFusionEnergy(fusionLevel, energyTechLevel);
            fusionCost = gameBalancer.getFusionGasCost(fusionLevel);
        }

        double totalEnergy = solarEnergy + fusionEnergy;
        double netEnergy = totalEnergy - mineConsumption;
        boolean isDeficit = netEnergy < 0;

        double metalRate = gameBalancer.getMetalProductionPerHour(metalMineLevel, isDeficit);
        double crystalRate = gameBalancer.getCrystalProductionPerHour(crystalMineLevel, isDeficit);
        double gasRate = gameBalancer.getGasProductionPerHour(gasMineLevel, planet.getTemperature(), isDeficit);

        Map<String, Double> result = new HashMap<>();
        result.put("metalRate", metalRate);
        result.put("crystalRate", crystalRate);
        result.put("gasRate", gasRate);
        result.put("netEnergy", netEnergy);
        result.put("energyConsumption", mineConsumption);
        result.put("fusionEnergy", fusionEnergy);
        result.put("fusionCost", fusionCost);
        return result;
    }

    @Transactional
    public void tickResources() {
        List<Planet> planets = planetRepository.findAll();
        for (Planet planet : planets) {
            Instant now = Instant.now();
            double hoursElapsed = ChronoUnit.SECONDS.between(planet.getLastUpdated(), now) / 3600.0;
            if (hoursElapsed <= 0) continue;

            Map<String, Double> rates = getProductionRates(planet);
            Map<String, Double> caps = getStorageCaps(planet);

            double metalRate = rates.get("metalRate");
            double crystalRate = rates.get("crystalRate");
            double gasRate = rates.get("gasRate");
            double fusionCost = rates.get("fusionCost");

            double metalAccrued = metalRate * hoursElapsed;
            double crystalAccrued = crystalRate * hoursElapsed;
            double gasAccrued = gasRate * hoursElapsed;
            double gasConsumed = fusionCost * hoursElapsed;

            planet.setMetal(Math.min(planet.getMetal() + metalAccrued, caps.get("metalStorage")));
            planet.setCrystal(Math.min(planet.getCrystal() + crystalAccrued, caps.get("crystalStorage")));
            double newGas = planet.getGas() + gasAccrued - gasConsumed;
            planet.setGas(Math.max(0, Math.min(newGas, caps.get("gasStorage"))));
            planet.setLastUpdated(now);
        }
        planetRepository.saveAll(planets);
    }
}
```

Add imports:
```java
import java.time.Instant;
import java.time.temporal.ChronoUnit;
```

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Expected: BUILD SUCCESS

---

### Task 4: Refactor existing services to use EconomyService

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/BuildingService.java`
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/ShipyardService.java`
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/ResearchService.java`
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/FleetService.java`
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/ResourceService.java` (merge into EconomyService)

- [ ] **Refactor BuildingService**

Inject `EconomyService economyService`. Replace inline resource check/deduct in the upgrade method:

```java
if (!economyService.checkAndDeduct(planetId, metalCost, crystalCost, gasCost)) {
    throw new InsufficientResourcesException("Not enough resources");
}
```
Remove the old inline check and `planet.setMetal(...)`, `planet.setCrystal(...)`, `planet.setGas(...)`, `planetRepository.save(planet)` lines.

In the cancel/refund path, replace with:
```java
economyService.refund(queue.getPlanetId(), queue.getMetalCost(), queue.getCrystalCost(), queue.getGasCost());
```

- [ ] **Refactor ShipyardService**

Same pattern: inject EconomyService, replace ship build resource deduction with `economyService.checkAndDeduct()`, replace refund with `economyService.refund()`.

- [ ] **Refactor ResearchService**

Same pattern: inject EconomyService, replace research cost deduction with `economyService.checkAndDeduct()`, replace refund with `economyService.refund()`.

- [ ] **Refactor FleetService — Transport mission**

In the transport launch path, replace the resource deduction with `economyService.checkAndDeduct()`.

In the transport arrival path (return), replace `addResources` with `economyService.addResources()`.

In the recycle mission return path, replace with `economyService.addResources()`.

In the combat loot return path, replace with `economyService.addResources()`.

- [ ] **Merge ResourceService into EconomyService**

The existing `ResourceService.recalculateResources()` does similar time-based accrual — merge this logic into `EconomyService.tickResources()` and remove the old `ResourceService.java` file (or keep as thin delegate).

- [ ] **Verify compiles and tests pass**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am test -DskipTests=false 2>&1 | tail -15`
Expected: BUILD SUCCESS

---

### Task 5: GameLoop integration

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/GameLoopService.java`

- [ ] **Read GameLoopService.java** to find where scheduled tasks run

- [ ] **Add tickResources call every 10 seconds**

After the fleet processing call, add:
```java
    economyService.tickResources();
```

Inject `EconomyService economyService` into GameLoopService constructor.

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Expected: BUILD SUCCESS

---

### Task 6: PlanetResources endpoint

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/web/PlanetController.java`

- [ ] **Add GET /planets/{id}/resources endpoint**

In PlanetController, add:

```java
    @GetMapping("/planets/{id}/resources")
    public ResponseEntity<?> getPlanetResources(@PathVariable Long id) {
        return ResponseEntity.ok(economyService.getCurrentResources(id));
    }
```

Inject `EconomyService economyService` into PlanetController constructor.

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Expected: BUILD SUCCESS

---

### Task 7: Frontend models + services

**Files:**
- Modify: `frontend/src/app/core/models/models.ts`
- Modify: `frontend/src/app/core/services/game.service.ts`
- Create: `frontend/src/app/core/services/planet-state.service.ts`

- [ ] **Add PlanetResourcesResponse interface**

In `models.ts`, add:

```typescript
export interface PlanetResourcesResponse {
  planetId: number;
  metal: number;
  crystal: number;
  gas: number;
  energy: number;
  metalRate: number;
  crystalRate: number;
  gasRate: number;
  metalStorage: number;
  crystalStorage: number;
  gasStorage: number;
  energyConsumption: number;
}
```

- [ ] **Add getPlanetResources method**

In `game.service.ts`, add:

```typescript
  getPlanetResources(planetId: number) {
    return this.http.get<PlanetResourcesResponse>(`${environment.apiUrl}/game/planets/${planetId}/resources`);
  }
```

Update import to include `PlanetResourcesResponse`.

- [ ] **Create PlanetStateService**

```typescript
import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PlanetStateService {
  private activePlanetIdSource = new BehaviorSubject<number | null>(null);
  activePlanetId$ = this.activePlanetIdSource.asObservable();

  setActivePlanet(planetId: number) {
    this.activePlanetIdSource.next(planetId);
  }
}
```

- [ ] **Verify frontend builds**

Run: `cd /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend && npx ng build --configuration production 2>&1 | tail -10`
Expected: BUILD SUCCESS (may warn about missing ResourceBarComponent — expected)

---

### Task 8: Frontend ResourceBarComponent

**Files:**
- Create: `frontend/src/app/resource-bar/resource-bar.component.ts`

- [ ] **Create ResourceBarComponent**

```typescript
import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, interval } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { GameService } from '../core/services/game.service';
import { PlanetStateService } from '../core/services/planet-state.service';
import { PlanetResourcesResponse } from '../core/models/models';

@Component({
  selector: 'app-resource-bar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="resource-bar" *ngIf="resources">
      <span class="resource planet-name">{{ planetName }}</span>
      <span class="resource metal">&#9679; Metal: <b>{{ fmt(resources.metal) }}</b>
        <span class="cap">/ {{ fmt(resources.metalStorage) }}</span>
        <span class="rate" title="Per hour">+{{ fmt(resources.metalRate) }}/h</span>
      </span>
      <span class="resource crystal">&#9679; Crystal: <b>{{ fmt(resources.crystal) }}</b>
        <span class="cap">/ {{ fmt(resources.crystalStorage) }}</span>
        <span class="rate" title="Per hour">+{{ fmt(resources.crystalRate) }}/h</span>
      </span>
      <span class="resource gas">&#9679; Gas: <b>{{ fmt(resources.gas) }}</b>
        <span class="cap">/ {{ fmt(resources.gasStorage) }}</span>
        <span class="rate" title="Per hour">+{{ fmt(resources.gasRate) }}/h</span>
      </span>
      <span class="resource energy" [class.negative]="resources.energy < 0">
        &#9889; Energy: <b>{{ resources.energy >= 0 ? '+' : '' }}{{ resources.energy | number:'1.0-0' }}</b>
      </span>
    </div>
  `,
  styles: [`
    .resource-bar { display: flex; gap: 20px; padding: 6px 16px; background: #111; border-bottom: 1px solid #333; font-size: 12px; align-items: center; flex-wrap: wrap; }
    .resource { white-space: nowrap; }
    .planet-name { color: #ffd700; font-weight: bold; margin-right: 8px; }
    .metal { color: #4af; }
    .crystal { color: #4dd; }
    .gas { color: #f44; }
    .energy { color: #ff0; }
    .energy.negative { color: #f44; }
    .cap { color: #555; font-size: 11px; }
    .rate { color: #4a4; font-size: 10px; margin-left: 2px; }
  `]
})
export class ResourceBarComponent implements OnInit, OnDestroy {
  resources: PlanetResourcesResponse | null = null;
  planetName = '';
  private sub = new Subscription();

  constructor(
    private gameService: GameService,
    private planetState: PlanetStateService
  ) {}

  ngOnInit() {
    this.sub.add(
      this.planetState.activePlanetId$.pipe(
        switchMap(planetId => {
          if (!planetId) return [];
          return interval(10000).pipe(
            switchMap(() => this.gameService.getPlanetResources(planetId))
          );
        })
      ).subscribe(data => {
        this.resources = data;
      })
    );
    // Initial fetch
    const pid = this.planetState['activePlanetIdSource'].value;
    if (pid) {
      this.gameService.getPlanetResources(pid).subscribe(data => {
        this.resources = data;
      });
    }
  }

  ngOnDestroy() {
    this.sub.unsubscribe();
  }

  fmt(n: number): string {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
    if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
    return Math.floor(n).toLocaleString();
  }
}
```

- [ ] **Verify frontend builds**

Run: `cd /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend && npx ng build --configuration production 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

### Task 9: Frontend integration

**Files:**
- Modify: `frontend/src/app/app.component.ts`
- Modify: `frontend/src/app/overview/overview.component.ts`
- Modify: `frontend/src/app/resources/resources.component.ts`
- Modify: `frontend/src/app/overview/overview.component.html` (if template is separate; if inline, change inline)

- [ ] **Add ResourceBarComponent to AppComponent**

In `app.component.ts`, import `ResourceBarComponent` and add it to the template before `<router-outlet>`:

```typescript
import { ResourceBarComponent } from './resource-bar/resource-bar.component';

// In template:
//   <app-resource-bar></app-resource-bar>
//   <router-outlet></router-outlet>
```

Add `ResourceBarComponent` to the `imports` array.

- [ ] **Set active planet on navigation**

In `AppComponent`, inject `PlanetStateService`, and in `ngOnInit` or via router events, set the active planet from the first owned planet or the current route.

Import `PlanetStateService`:
```typescript
import { PlanetStateService } from './core/services/planet-state.service';
```

Inject in constructor:
```typescript
constructor(private planetState: PlanetStateService) {}
```

In `ngOnInit`, fetch the player's planets and set the first one:
```typescript
this.gameService.getMyPlanets().subscribe(planets => {
  if (planets.length > 0) {
    this.planetState.setActivePlanet(planets[0].id);
  }
});
```

- [ ] **Remove inline resource bar from OverviewComponent**

In `overview/overview.component.ts`, remove the resource display section (metal, crystal, gas, energy lines). Keep the building grid and upgrade panel.

- [ ] **Remove inline resource bar from ResourcesComponent**

In `resources/resources.component.ts`, remove the inline resource bar at the top of the template.

Note: The resource bar in OverviewComponent shows current metal/crystal/gas/energy. With the header bar present, these are redundant.

- [ ] **Verify frontend builds**

Run: `cd /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend && npx ng build --configuration production 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

### Task 10: Final verification

**Files:**
- No code changes

- [ ] **Full backend build + tests**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am clean verify -DskipTests=false 2>&1 | tail -15`
Expected: BUILD SUCCESS

- [ ] **Full frontend build**

Run: `cd /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend && npx ng build --configuration production 2>&1 | tail -10`
Expected: BUILD SUCCESS (only pre-existing CommonJS warning)

- [ ] **Report results**

Report backend and frontend build status.
