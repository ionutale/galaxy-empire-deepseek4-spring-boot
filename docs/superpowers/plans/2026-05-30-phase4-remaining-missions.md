# Phase 4: Remaining Fleet Missions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Transport, Colonize, Spy, and Recycle missions extending the Phase 3 fleet system.

**Architecture:** All 4 missions reuse the existing Fleet lifecycle (launch → FleetService.launchFleet → DB → GameLoop.processArrivals/processReturns). Mission-specific behavior is handled by branching on FleetMission in FleetService. One new entity (EspionageReport) and one new repository added. Frontend FleetComponent extended with mission-specific UI sections.

**Tech Stack:** Spring Boot 3.4 + Java 21, PostgreSQL 16, Angular 19, Flyway

---

### Task 1: FleetMission enum + V7 migration

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/domain/FleetMission.java`
- Create: `backend/game-service/src/main/resources/db/migration/V7__add_espionage_report.sql`

- [ ] **Step 1: Update FleetMission enum**

Add TRANSPORT, COLONIZE, SPY, RECYCLE:
```java
public enum FleetMission {
    ATTACK,
    DEPLOY,
    TRANSPORT,
    COLONIZE,
    SPY,
    RECYCLE
}
```

- [ ] **Step 2: Create V7 migration**

```sql
CREATE TABLE espionage_report (
    id BIGSERIAL PRIMARY KEY,
    attacker_id BIGINT NOT NULL,
    defender_id BIGINT NOT NULL,
    target_planet_id BIGINT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resources_json JSONB NOT NULL DEFAULT '{}',
    ships_json JSONB NOT NULL DEFAULT '{}',
    buildings_json JSONB NOT NULL DEFAULT '{}',
    technologies_json JSONB NOT NULL DEFAULT '{}',
    defenses_json JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_espionage_report_target_planet ON espionage_report(target_planet_id);
CREATE INDEX idx_espionage_report_defender ON espionage_report(defender_id);

-- Ensure no existing fleets use the new enum values (they don't, so this is safe)
```

- [ ] **Step 3: Commit**

---

### Task 2: EspionageReport entity + repository

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/EspionageReport.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/EspionageReportRepository.java`

- [ ] **Step 1: Create EspionageReport entity**

```java
package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "espionage_report")
public class EspionageReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attacker_id", nullable = false)
    private Long attackerId;

    @Column(name = "defender_id", nullable = false)
    private Long defenderId;

    @Column(name = "target_planet_id", nullable = false)
    private Long targetPlanetId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "resources_json", columnDefinition = "jsonb", nullable = false)
    private String resourcesJson;

    @Column(name = "ships_json", columnDefinition = "jsonb", nullable = false)
    private String shipsJson;

    @Column(name = "buildings_json", columnDefinition = "jsonb", nullable = false)
    private String buildingsJson;

    @Column(name = "technologies_json", columnDefinition = "jsonb", nullable = false)
    private String technologiesJson;

    @Column(name = "defenses_json", columnDefinition = "jsonb", nullable = false)
    private String defensesJson;

    public EspionageReport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAttackerId() { return attackerId; }
    public void setAttackerId(Long attackerId) { this.attackerId = attackerId; }
    public Long getDefenderId() { return defenderId; }
    public void setDefenderId(Long defenderId) { this.defenderId = defenderId; }
    public Long getTargetPlanetId() { return targetPlanetId; }
    public void setTargetPlanetId(Long targetPlanetId) { this.targetPlanetId = targetPlanetId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getResourcesJson() { return resourcesJson; }
    public void setResourcesJson(String resourcesJson) { this.resourcesJson = resourcesJson; }
    public String getShipsJson() { return shipsJson; }
    public void setShipsJson(String shipsJson) { this.shipsJson = shipsJson; }
    public String getBuildingsJson() { return buildingsJson; }
    public void setBuildingsJson(String buildingsJson) { this.buildingsJson = buildingsJson; }
    public String getTechnologiesJson() { return technologiesJson; }
    public void setTechnologiesJson(String technologiesJson) { this.technologiesJson = technologiesJson; }
    public String getDefensesJson() { return defensesJson; }
    public void setDefensesJson(String defensesJson) { this.defensesJson = defensesJson; }
}
```

- [ ] **Step 2: Create EspionageReportRepository**

```java
package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.EspionageReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EspionageReportRepository extends JpaRepository<EspionageReport, Long> {
    List<EspionageReport> findByTargetPlanetIdOrderByTimestampDesc(Long targetPlanetId);
    List<EspionageReport> findByDefenderIdOrderByTimestampDesc(Long defenderId);
}
```

- [ ] **Step 3: Verify compilation**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Expected: BUILD SUCCESS

---

### Task 3: PlanetService.createPlanetAt method

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/PlanetService.java`

- [ ] **Step 1: Add createPlanetAt method**

After `createStarterPlanet`, add:
```java
@Transactional
public Planet createPlanetAt(Long playerId, int galaxy, int systemId, int slot) {
    if (galaxy < 1 || galaxy > 9 || systemId < 1 || systemId > 500 || slot < 1 || slot > 15) {
        throw new IllegalArgumentException("Invalid coordinates");
    }
    if (planetRepository.existsByGalaxyAndSystemIdAndSlot(galaxy, systemId, slot)) {
        throw new IllegalArgumentException("Planet already exists at these coordinates");
    }
    int temperature = randomTemperature(slot);
    Planet planet = new Planet();
    planet.setPlayerId(playerId);
    planet.setName("Colony");
    planet.setGalaxy(galaxy);
    planet.setSystemId(systemId);
    planet.setSlot(slot);
    planet.setTemperature(temperature);
    planet = planetRepository.save(planet);

    List<Building> starters = Arrays.asList(
        new Building(planet.getId(), BuildingType.METAL_MINE, 1, 0),
        new Building(planet.getId(), BuildingType.CRYSTAL_MINE, 1, 1),
        new Building(planet.getId(), BuildingType.GAS_MINE, 1, 2),
        new Building(planet.getId(), BuildingType.SOLAR_PLANT, 1, 3),
        new Building(planet.getId(), BuildingType.METAL_STORAGE, 1, 4),
        new Building(planet.getId(), BuildingType.CRYSTAL_STORAGE, 1, 5),
        new Building(planet.getId(), BuildingType.GAS_STORAGE, 1, 6),
        new Building(planet.getId(), BuildingType.ROBOT_FACTORY, 0, 7),
        new Building(planet.getId(), BuildingType.RESEARCH_LAB, 0, 8),
        new Building(planet.getId(), BuildingType.SHIPYARD, 0, 9)
    );
    buildingRepository.saveAll(starters);
    return planet;
}
```

- [ ] **Step 2: Verify compilation**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Expected: BUILD SUCCESS

---

### Task 4: Transport mission in FleetService

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/FleetService.java`

- [ ] **Step 1: Change launchFleet signature**

Current:
```java
public Map<String, Object> launchFleet(Long originPlanetId, Long targetPlanetId,
                                        FleetMission mission, Map<String, Integer> ships,
                                        Long playerId)
```

Change to:
```java
public Map<String, Object> launchFleet(Long originPlanetId, Long targetPlanetId,
                                        FleetMission mission, Map<String, Integer> ships,
                                        Long playerId, Map<String, Object> missionParams)
```

- [ ] **Step 2: Add TRANSPORT launch validation after the DEPLOY block**

After the DEPLOY block (after line 63's closing brace), add:
```java
        if (mission == FleetMission.TRANSPORT) {
            Planet target = planetRepository.findById(targetPlanetId)
                .orElseThrow(() -> new IllegalArgumentException("Target planet not found"));
            if (!target.getPlayerId().equals(playerId)) {
                throw new IllegalArgumentException("Can only transport to own planets");
            }
            double metal = Double.parseDouble(missionParams.getOrDefault("metal", "0").toString());
            double crystal = Double.parseDouble(missionParams.getOrDefault("crystal", "0").toString());
            double gas = Double.parseDouble(missionParams.getOrDefault("gas", "0").toString());
            if (metal <= 0 && crystal <= 0 && gas <= 0) {
                throw new IllegalArgumentException("Must transport at least one resource type");
            }
            double totalCargo = ships.entrySet().stream()
                .mapToDouble(e -> gameBalancer.getShipCargo(ShipType.valueOf(e.getKey())) * e.getValue())
                .sum();
            if (metal + crystal + gas > totalCargo) {
                throw new IllegalArgumentException("Resource amount exceeds cargo capacity");
            }
            Planet origin = planetRepository.findById(originPlanetId).orElseThrow();
            if (origin.getMetal() < metal || origin.getCrystal() < crystal || origin.getGas() < gas) {
                throw new IllegalArgumentException("Insufficient resources at origin planet");
            }
            origin.setMetal(origin.getMetal() - metal);
            origin.setCrystal(origin.getCrystal() - crystal);
            origin.setGas(origin.getGas() - gas);
            planetRepository.save(origin);
        }
```

- [ ] **Step 3: Add COLONIZE launch validation after above**

```java
        if (mission == FleetMission.COLONIZE) {
            boolean hasColonyShip = ships.entrySet().stream()
                .anyMatch(e -> ShipType.valueOf(e.getKey()) == ShipType.COLONY_SHIP && e.getValue() > 0);
            if (!hasColonyShip) {
                throw new IllegalArgumentException("Colonize mission requires at least 1 Colony Ship");
            }
        }
```

- [ ] **Step 4: Add SPY launch validation after above**

```java
        if (mission == FleetMission.SPY) {
            Planet target = planetRepository.findById(targetPlanetId)
                .orElseThrow(() -> new IllegalArgumentException("Target planet not found"));
            if (target.getPlayerId().equals(playerId)) {
                throw new IllegalArgumentException("Cannot spy on own planet");
            }
            boolean hasProbe = ships.entrySet().stream()
                .anyMatch(e -> ShipType.valueOf(e.getKey()) == ShipType.ESPIONAGE_PROBE && e.getValue() > 0);
            if (!hasProbe) {
                throw new IllegalArgumentException("Spy mission requires at least 1 Espionage Probe");
            }
        }
```

- [ ] **Step 5: Add RECYCLE launch validation after above**

```java
        if (mission == FleetMission.RECYCLE) {
            Optional<DebrisField> df = debrisFieldRepository.findByPlanetId(targetPlanetId);
            if (df.isEmpty() || (df.get().getMetal() <= 0 && df.get().getCrystal() <= 0)) {
                throw new IllegalArgumentException("No debris field at target planet");
            }
            boolean hasRecycler = ships.entrySet().stream()
                .anyMatch(e -> ShipType.valueOf(e.getKey()) == ShipType.RECYCLER && e.getValue() > 0);
            if (!hasRecycler) {
                throw new IllegalArgumentException("Recycle mission requires at least 1 Recycler");
            }
        }
```

- [ ] **Step 6: Set loot on Fleet for TRANSPORT**

After `fleet.setStatus(FleetStatus.EN_ROUTE)` (around line 94), before the fleetShipRepository save loop, add:
```java
        if (mission == FleetMission.TRANSPORT) {
            fleet.setMetalLoot(Double.parseDouble(missionParams.getOrDefault("metal", "0").toString()));
            fleet.setCrystalLoot(Double.parseDouble(missionParams.getOrDefault("crystal", "0").toString()));
            fleet.setGasLoot(Double.parseDouble(missionParams.getOrDefault("gas", "0").toString()));
        }
```

- [ ] **Step 7: Add TRANSPORT arrival logic in processArrivals**

In the `processArrivals` for loop, after the DEPLOY block's closing brace (before the catch), add:
```java
                } else if (fleet.getMission() == FleetMission.TRANSPORT) {
                    Planet target = planetRepository.findById(fleet.getTargetPlanetId()).orElse(null);
                    if (target != null) {
                        target.setMetal(target.getMetal() + fleet.getMetalLoot());
                        target.setCrystal(target.getCrystal() + fleet.getCrystalLoot());
                        target.setGas(target.getGas() + fleet.getGasLoot());
                        planetRepository.save(target);
                    }
                    fleet.setStatus(FleetStatus.RETURNING);
                    long travelTime = java.time.Duration.between(fleet.getDepartureTime(), fleet.getArrivalTime()).getSeconds();
                    fleet.setReturnTime(Instant.now().plusSeconds(travelTime));
                    fleetRepository.save(fleet);
```

- [ ] **Step 8: Add COLONIZE arrival logic**

After the TRANSPORT block, add:
```java
                } else if (fleet.getMission() == FleetMission.COLONIZE) {
                    // Consume 1 Colony Ship
                    for (FleetShip fs : ships) {
                        if (fs.getShipType() == ShipType.COLONY_SHIP && fs.getQuantity() > 0) {
                            fs.setQuantity(fs.getQuantity() - 1);
                            fleetShipRepository.save(fs);
                            break;
                        }
                    }
                    // Remaining ships return
                    boolean anySurvivors = ships.stream().anyMatch(fs -> fs.getQuantity() > 0);
                    if (anySurvivors) {
                        fleet.setStatus(FleetStatus.RETURNING);
                        long travelTime = java.time.Duration.between(fleet.getDepartureTime(), fleet.getArrivalTime()).getSeconds();
                        fleet.setReturnTime(Instant.now().plusSeconds(travelTime));
                    } else {
                        fleet.setStatus(FleetStatus.ARRIVED);
                        fleet.setReturnTime(null);
                    }
                    fleetRepository.save(fleet);
```

- [ ] **Step 9: Add SPY arrival logic**

After the COLONIZE block, add:
```java
                } else if (fleet.getMission() == FleetMission.SPY) {
                    // Compare espionage tech levels
                    int attackerEspLevel = playerTechnologyRepository
                        .findByPlayerIdAndTechnology(fleet.getPlayerId(), Technology.ESPIONAGE_TECH)
                        .map(PlayerTechnology::getLevel)
                        .orElse(0);
                    Planet targetPlanet = planetRepository.findById(fleet.getTargetPlanetId()).orElse(null);
                    int defenderEspLevel = 0;
                    if (targetPlanet != null) {
                        defenderEspLevel = playerTechnologyRepository
                            .findByPlayerIdAndTechnology(targetPlanet.getPlayerId(), Technology.ESPIONAGE_TECH)
                            .map(PlayerTechnology::getLevel)
                            .orElse(0);
                    }
                    // All probes are consumed regardless
                    for (FleetShip fs : ships) {
                        fs.setQuantity(0);
                        fleetShipRepository.save(fs);
                    }
                    if (attackerEspLevel > defenderEspLevel && targetPlanet != null) {
                        // Probes survive - generate report
                        EspionageReport report = new EspionageReport();
                        report.setAttackerId(fleet.getPlayerId());
                        report.setDefenderId(targetPlanet.getPlayerId());
                        report.setTargetPlanetId(fleet.getTargetPlanetId());
                        report.setTimestamp(Instant.now());
                        // Gather intel based on tech difference
                        int diff = attackerEspLevel - defenderEspLevel;
                        ObjectMapper mapper = new ObjectMapper();
                        try {
                            report.setResourcesJson(mapper.writeValueAsString(Map.of(
                                "metal", targetPlanet.getMetal(),
                                "crystal", targetPlanet.getCrystal(),
                                "gas", targetPlanet.getGas()
                            )));
                            if (diff >= 1) {
                                List<PlanetShip> planetShips = planetShipRepository.findByPlanetId(fleet.getTargetPlanetId());
                                Map<String, Integer> shipMap = new HashMap<>();
                                for (PlanetShip ps : planetShips) {
                                    shipMap.put(ps.getShipType().name(), ps.getQuantity());
                                }
                                report.setShipsJson(mapper.writeValueAsString(shipMap));
                            }
                            if (diff >= 2) {
                                List<Building> buildings = buildingRepository.findByPlanetId(fleet.getTargetPlanetId());
                                Map<String, Integer> buildingMap = new HashMap<>();
                                for (Building b : buildings) {
                                    if (b.getLevel() > 0) {
                                        buildingMap.put(b.getBuildingType().name(), b.getLevel());
                                    }
                                }
                                report.setBuildingsJson(mapper.writeValueAsString(buildingMap));
                            }
                            if (diff >= 3) {
                                List<PlayerTechnology> techs = playerTechnologyRepository.findByPlayerId(targetPlanet.getPlayerId());
                                Map<String, Integer> techMap = new HashMap<>();
                                for (PlayerTechnology pt : techs) {
                                    if (pt.getLevel() > 0) {
                                        techMap.put(pt.getTechnology().name(), pt.getLevel());
                                    }
                                }
                                report.setTechnologiesJson(mapper.writeValueAsString(techMap));
                            }
                        } catch (Exception ignored) {}
                        espionageReportRepository.save(report);
                    }
                    fleet.setStatus(FleetStatus.RETURNING);
                    fleet.setReturnTime(Instant.now());
                    fleetRepository.save(fleet);
```

- [ ] **Step 10: Add RECYCLE arrival logic**

After the SPY block, add:
```java
                } else if (fleet.getMission() == FleetMission.RECYCLE) {
                    Optional<DebrisField> dfOpt = debrisFieldRepository.findByPlanetId(fleet.getTargetPlanetId());
                    if (dfOpt.isPresent()) {
                        DebrisField df = dfOpt.get();
                        int recyclerCount = 0;
                        for (FleetShip fs : ships) {
                            if (fs.getShipType() == ShipType.RECYCLER) {
                                recyclerCount += fs.getQuantity();
                            }
                        }
                        double cargoCapacity = recyclerCount * gameBalancer.getShipCargo(ShipType.RECYCLER);
                        double collectMetal = Math.min(df.getMetal(), cargoCapacity);
                        double collectCrystal = Math.min(df.getCrystal(), cargoCapacity - collectMetal);
                        if (collectCrystal < df.getCrystal() && collectMetal < cargoCapacity) {
                            collectCrystal = Math.min(df.getCrystal(), cargoCapacity - collectMetal);
                        }
                        fleet.setMetalLoot(collectMetal);
                        fleet.setCrystalLoot(collectCrystal);
                        df.setMetal(df.getMetal() - collectMetal);
                        df.setCrystal(df.getCrystal() - collectCrystal);
                        debrisFieldRepository.save(df);
                    }
                    fleet.setStatus(FleetStatus.RETURNING);
                    long travelTime = java.time.Duration.between(fleet.getDepartureTime(), fleet.getArrivalTime()).getSeconds();
                    fleet.setReturnTime(Instant.now().plusSeconds(travelTime));
                    fleetRepository.save(fleet);
                }
```

Wait, I'm adding imports that are needed - ObjectMapper, PlayerTechnologyRepository, EspionageReportRepository, BuildingRepository. Let me check what imports the FleetService already has and what new ones are needed.

The existing imports are:
```java
import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
```

The `com.galaxyempire.game.domain.*` should catch EspionageReport, PlayerTechnology, Technology. The `com.galaxyempire.game.repository.*` should catch EspionageReportRepository, PlayerTechnologyRepository.

But we also need `com.fasterxml.jackson.databind.ObjectMapper`, and `java.util.Optional` is already covered by `java.util.*`.

But `BuildingRepository` is not in the repository list... actually `com.galaxyempire.game.repository.*` should catch it since it's in the same package.

- [ ] **Step 11: Add ObjectMapper import to FleetService.java**

```java
import com.fasterxml.jackson.databind.ObjectMapper;
```

Add after the existing `import com.galaxyempire.game.repository.*;` line.

- [ ] **Step 12: Update FleetService constructor and fields**

Add new dependencies:
```java
    private final EspionageReportRepository espionageReportRepository;
    private final PlayerTechnologyRepository playerTechnologyRepository;
    private final BuildingRepository buildingRepository;
```

Add to constructor:
```java
                      EspionageReportRepository espionageReportRepository,
                      PlayerTechnologyRepository playerTechnologyRepository,
                      BuildingRepository buildingRepository)
```

Add constructor assignments:
```java
        this.espionageReportRepository = espionageReportRepository;
        this.playerTechnologyRepository = playerTechnologyRepository;
        this.buildingRepository = buildingRepository;
```

- [ ] **Step 13: Update FleetController call to launchFleet to pass missionParams**

The controller currently calls:
```java
var result = fleetService.launchFleet(planetId, targetPlanetId, mission, ships, playerId);
```
Change to:
```java
var result = fleetService.launchFleet(planetId, targetPlanetId, mission, ships, playerId, body);
```

This will be done properly in Task 8 (FleetController), but for now stub it with `body` as the missionParams map.

- [ ] **Step 14: Verify compilation**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

### Task 5: FleetController changes

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/web/FleetController.java`
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/FleetService.java`

- [ ] **Step 1: Update launchFleet call in controller to pass body as missionParams**

```java
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
```

Need to inject PlanetService into FleetController:
```java
private final PlanetService planetService;

public FleetController(FleetService fleetService, PlanetService planetService) {
    this.fleetService = fleetService;
    this.planetService = planetService;
}
```

- [ ] **Step 2: Add espionage reports endpoint**

```java
@GetMapping("/planets/{planetId}/espionage-reports")
public ResponseEntity<?> getEspionageReports(@PathVariable Long planetId) {
    return ResponseEntity.ok(fleetService.getPlanetEspionageReports(planetId));
}
```

- [ ] **Step 3: Add getPlanetEspionageReports to FleetService**

```java
@Transactional(readOnly = true)
public List<EspionageReport> getPlanetEspionageReports(Long planetId) {
    return espionageReportRepository.findByTargetPlanetIdOrderByTimestampDesc(planetId);
}
```

- [ ] **Step 4: Verify compilation**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

### Task 6: Frontend models + service methods

**Files:**
- Modify: `frontend/src/app/core/models/models.ts`
- Modify: `frontend/src/app/core/services/game.service.ts`

- [ ] **Step 1: Add EspionageReport interface to models.ts**

```typescript
export interface EspionageReport {
  id: number;
  attackerId: number;
  defenderId: number;
  targetPlanetId: number;
  timestamp: string;
  resourcesJson: string;
  shipsJson: string;
  buildingsJson: string;
  technologiesJson: string;
  defensesJson: string;
}
```

- [ ] **Step 2: Update launchFleet signature and add espionage reports method**

Change the existing `launchFleet` to accept a body object (since request body varies per mission):
```typescript
  launchFleet(planetId: number, body: any) {
    return this.http.post(`${environment.apiUrl}/game/planets/${planetId}/fleet`, body);
  }
```

Add the espionage reports method:
```typescript
  getEspionageReports(planetId: number) {
    return this.http.get<EspionageReport[]>(`${environment.apiUrl}/game/planets/${planetId}/espionage-reports`);
  }
```

Also update the import:
```typescript
import { Fleet, CombatReport, DebrisField, EspionageReport } from '../models/models';
```

- [ ] **Step 3: Verify frontend build**

Run: `npx ng build --configuration production 2>&1 | tail -10`
Expected: Build successful

---

### Task 7: Frontend FleetComponent mission UI

**Files:**
- Modify: `frontend/src/app/fleet/fleet.component.ts`

- [ ] **Step 1: Add EspionageReport import**

```typescript
import { PlanetShip, Fleet, DebrisField, EspionageReport } from '../core/models/models';
```

- [ ] **Step 2: Add mission-specific fields to component class**

```typescript
  // Transport
  transportMetal = 0;
  transportCrystal = 0;
  transportGas = 0;
  transportCargoUsed = 0;
  transportCargoTotal = 0;

  // Colonize
  targetGalaxy = 1;
  targetSystemId = 1;
  targetSlot = 1;

  // Espionage reports
  espionageReports: EspionageReport[] = [];
```

- [ ] **Step 3: Update loadData to fetch espionage reports**

```typescript
  loadData() {
    this.gameService.getPlanetShips(this.planetId).subscribe(ships => {
      this.planetShips = ships;
    });
    this.gameService.getPlanetFleets(this.planetId).subscribe(fleets => {
      this.activeFleets = fleets;
    });
    this.gameService.getDebrisField(this.planetId).subscribe(df => {
      this.debrisField = df;
    });
    this.gameService.getEspionageReports(this.planetId).subscribe(reports => {
      this.espionageReports = reports;
    });
  }
```

- [ ] **Step 4: Update mission selector options**

Replace the static `<select>` options with all 5 missions:
```html
          <select [(ngModel)]="mission">
            <option value="ATTACK">Attack</option>
            <option value="DEPLOY">Deploy</option>
            <option value="TRANSPORT">Transport</option>
            <option value="COLONIZE">Colonize</option>
            <option value="SPY">Spy</option>
            <option value="RECYCLE">Recycle</option>
          </select>
```

- [ ] **Step 5: Add Transport resource inputs (shown when mission === 'TRANSPORT')**

After the ship quantity inputs (after the `*ngFor` loop for ships), add:
```html
          <div *ngIf="mission === 'TRANSPORT'" class="transport-resources">
            <h4>Resources to Transport</h4>
            <div class="form-row">
              <label>Metal:</label>
              <input type="number" [(ngModel)]="transportMetal" min="0">
            </div>
            <div class="form-row">
              <label>Crystal:</label>
              <input type="number" [(ngModel)]="transportCrystal" min="0">
            </div>
            <div class="form-row">
              <label>Gas:</label>
              <input type="number" [(ngModel)]="transportGas" min="0">
            </div>
            <div class="cargo-info">Cargo: {{ transportCargoUsed }} / {{ transportCargoTotal }}</div>
          </div>
```

- [ ] **Step 6: Update cargo calculation on ship quantity change**

Add a method:
```typescript
  updateCargo() {
    let total = 0;
    for (const key of Object.keys(this.shipQuantities)) {
      const qty = this.shipQuantities[key] || 0;
      const shipType = key as any;
      const cargo = this.getShipCargo(shipType);
      total += cargo * qty;
    }
    this.transportCargoTotal = total;
    this.transportCargoUsed = this.transportMetal + this.transportCrystal + this.transportGas;
  }
```

And call it from the template on input change. Actually, using Angular's change detection we can compute it inline in the template or call it from ngModelChange.

Better: compute cargo in the template. But Float values from GameBalancer need a static mapping. Let me add a simple method:

Actually the simplest: just compute on the template side using a hardcoded cargo map. But that duplicates logic. Instead, let me just update `updateCargo` and call it in `(ngModelChange)` on the ship inputs.

Replace the ship input with:
```html
            <input type="number" [(ngModel)]="shipQuantities[ship.shipType]" min="0" [max]="ship.quantity" value="0" (ngModelChange)="updateCargo()">
```

Also call updateCargo on transport resource inputs:
```html
              <input type="number" [(ngModel)]="transportMetal" min="0" (ngModelChange)="updateCargo()">
```

- [ ] **Step 7: Add Colonize coordinate inputs**

After the Transport section, add:
```html
          <div *ngIf="mission === 'COLONIZE'" class="colonize-coords">
            <h4>Target Coordinates</h4>
            <div class="form-row">
              <label>Galaxy (1-9):</label>
              <input type="number" [(ngModel)]="targetGalaxy" min="1" max="9">
            </div>
            <div class="form-row">
              <label>System (1-500):</label>
              <input type="number" [(ngModel)]="targetSystemId" min="1" max="500">
            </div>
            <div class="form-row">
              <label>Slot (1-15):</label>
              <input type="number" [(ngModel)]="targetSlot" min="1" max="15">
            </div>
          </div>
```

- [ ] **Step 8: Update launchFleet to handle all mission types**

Replace the launchFleet method body. Before the API call, prepare different request bodies based on mission:

```typescript
  launchFleet() {
    const ships: { [key: string]: number } = {};
    let hasShips = false;
    for (const key of Object.keys(this.shipQuantities)) {
      const qty = this.shipQuantities[key] || 0;
      if (qty > 0) {
        ships[key] = qty;
        hasShips = true;
      }
    }
    if (!hasShips) {
      this.launchError = 'Select at least one ship';
      return;
    }
    this.launchError = null;

    let body: any = { mission: this.mission, ships };

    if (this.mission === 'COLONIZE') {
      body.galaxy = this.targetGalaxy;
      body.systemId = this.targetSystemId;
      body.slot = this.targetSlot;
    } else {
      if (!this.targetPlanetId) {
        this.launchError = 'Select a target planet';
        return;
      }
      body.targetPlanetId = this.targetPlanetId;
    }

    if (this.mission === 'TRANSPORT') {
      body.metal = this.transportMetal;
      body.crystal = this.transportCrystal;
      body.gas = this.transportGas;
    }

    this.gameService.launchFleet(this.planetId, body).subscribe({
      next: () => this.loadData(),
      error: (err) => this.launchError = err.error?.error || 'Launch failed'
    });
  }
```

- [ ] **Step 9: Add Espionage Reports section**

After the Debris Field section (before closing `</div>`), add:
```html
      <div class="section">
        <h3>Espionage Reports</h3>
        <div *ngFor="let report of espionageReports" class="report-card">
          <div class="report-header">Report from {{ report.timestamp | date:'short' }}</div>
          <div class="report-data" *ngIf="report.resourcesJson !== '{}'">
            <strong>Resources:</strong> {{ formatJson(report.resourcesJson) }}
          </div>
          <div class="report-data" *ngIf="report.shipsJson !== '{}'">
            <strong>Ships:</strong> {{ formatJson(report.shipsJson) }}
          </div>
          <div class="report-data" *ngIf="report.buildingsJson !== '{}'">
            <strong>Buildings:</strong> {{ formatJson(report.buildingsJson) }}
          </div>
          <div class="report-data" *ngIf="report.technologiesJson !== '{}'">
            <strong>Technologies:</strong> {{ formatJson(report.technologiesJson) }}
          </div>
        </div>
        <div *ngIf="espionageReports.length === 0" class="empty">No espionage reports.</div>
      </div>
```

Add formatJson helper:
```typescript
  formatJson(json: string): string {
    try {
      const obj = JSON.parse(json);
      return Object.entries(obj).map(([k, v]) => `${k}: ${v}`).join(', ');
    } catch {
      return json;
    }
  }
```

And add styles for the new sections:
```css
    .cargo-info { font-size: 12px; color: #4a9; margin-top: 4px; }
    .colonize-coords { margin-top: 8px; }
    .colonize-coords h4, .transport-resources h4 { color: #ffd700; font-size: 12px; margin: 8px 0; }
    .report-card { background: #221; border: 1px solid #a94; border-radius: 6px; padding: 8px; margin-bottom: 6px; font-size: 12px; }
    .report-header { color: #ffd700; font-weight: bold; margin-bottom: 4px; }
    .report-data { color: #ccc; margin: 2px 0; }
```

- [ ] **Step 10: Verify frontend build**

Run: `npx ng build --configuration production 2>&1 | tail -10`
Expected: Build successful

- [ ] **Step 11: Verify frontend tests**

Run: `npx ng test --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`
Expected: 1 test passes (or pre-existing failures only)

---

### Task 8: Verify backend compilation

- [ ] **Step 1: Full backend compile**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -20`
Expected: BUILD SUCCESS
