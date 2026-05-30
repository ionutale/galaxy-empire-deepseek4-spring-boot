# Phase 3: Fleet & Combat System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add fleet system with Attack and Deploy missions, rapid-fire combat, combat reports, and debris fields.

**Architecture:** Flyway migration → enums + entities → GameBalancer ship combat stats → CombatService → FleetService → FleetController + GameLoop processing → FleetComponent UI. Combat resolves in GameLoop tick on fleet arrival.

**Tech Stack:** Spring Boot 3.4, Java 21, PostgreSQL 16, Flyway, Angular 19

---

### Task 1: Database migration V6

**Files:**
- Create: `backend/game-service/src/main/resources/db/migration/V6__create_fleet_and_combat.sql`

- [ ] **Step 1: Create the migration**

```sql
CREATE TABLE fleet (
    id BIGSERIAL PRIMARY KEY,
    origin_planet_id BIGINT NOT NULL REFERENCES planet(id),
    target_planet_id BIGINT NOT NULL REFERENCES planet(id),
    player_id BIGINT NOT NULL,
    mission VARCHAR(16) NOT NULL,
    departure_time TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_time TIMESTAMP WITH TIME ZONE NOT NULL,
    return_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(16) NOT NULL DEFAULT 'EN_ROUTE',
    metal_loot DOUBLE PRECISION NOT NULL DEFAULT 0,
    crystal_loot DOUBLE PRECISION NOT NULL DEFAULT 0,
    gas_loot DOUBLE PRECISION NOT NULL DEFAULT 0
);

CREATE TABLE fleet_ship (
    id BIGSERIAL PRIMARY KEY,
    fleet_id BIGINT NOT NULL REFERENCES fleet(id),
    ship_type VARCHAR(32) NOT NULL,
    quantity INT NOT NULL
);

CREATE TABLE combat_report (
    id BIGSERIAL PRIMARY KEY,
    attacker_id BIGINT NOT NULL,
    defender_id BIGINT NOT NULL,
    attacker_planet_id BIGINT NOT NULL,
    defender_planet_id BIGINT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    result VARCHAR(16) NOT NULL,
    attacker_ships_before JSONB NOT NULL DEFAULT '{}',
    defender_ships_before JSONB NOT NULL DEFAULT '{}',
    attacker_ships_lost JSONB NOT NULL DEFAULT '{}',
    defender_ships_lost JSONB NOT NULL DEFAULT '{}',
    debris_metal DOUBLE PRECISION NOT NULL DEFAULT 0,
    debris_crystal DOUBLE PRECISION NOT NULL DEFAULT 0,
    resources_looted JSONB NOT NULL DEFAULT '{}',
    rounds JSONB NOT NULL DEFAULT '[]'
);

CREATE TABLE debris_field (
    id BIGSERIAL PRIMARY KEY,
    planet_id BIGINT NOT NULL UNIQUE REFERENCES planet(id),
    metal DOUBLE PRECISION NOT NULL DEFAULT 0,
    crystal DOUBLE PRECISION NOT NULL DEFAULT 0
);
```

- [ ] **Step 2: Build to verify Flyway picks it up**

Run: `cd backend && mvn -pl game-service -am compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 2: Enum classes (FleetMission, FleetStatus)

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/FleetMission.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/FleetStatus.java`

- [ ] **Step 1: Create FleetMission enum**

```java
package com.galaxyempire.game.domain;

public enum FleetMission {
    ATTACK, DEPLOY
}
```

- [ ] **Step 2: Create FleetStatus enum**

```java
package com.galaxyempire.game.domain;

public enum FleetStatus {
    EN_ROUTE, ARRIVED, RETURNING, RECALLED
}
```

---

### Task 3: JPA entities

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/Fleet.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/FleetShip.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/CombatReport.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/DebrisField.java`

- [ ] **Step 1: Create Fleet entity**

```java
package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fleet")
public class Fleet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "origin_planet_id", nullable = false)
    private Long originPlanetId;

    @Column(name = "target_planet_id", nullable = false)
    private Long targetPlanetId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FleetMission mission;

    @Column(name = "departure_time", nullable = false)
    private Instant departureTime;

    @Column(name = "arrival_time", nullable = false)
    private Instant arrivalTime;

    @Column(name = "return_time")
    private Instant returnTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FleetStatus status = FleetStatus.EN_ROUTE;

    @Column(name = "metal_loot", nullable = false)
    private double metalLoot;

    @Column(name = "crystal_loot", nullable = false)
    private double crystalLoot;

    @Column(name = "gas_loot", nullable = false)
    private double gasLoot;

    public Fleet() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOriginPlanetId() { return originPlanetId; }
    public void setOriginPlanetId(Long originPlanetId) { this.originPlanetId = originPlanetId; }
    public Long getTargetPlanetId() { return targetPlanetId; }
    public void setTargetPlanetId(Long targetPlanetId) { this.targetPlanetId = targetPlanetId; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public FleetMission getMission() { return mission; }
    public void setMission(FleetMission mission) { this.mission = mission; }
    public Instant getDepartureTime() { return departureTime; }
    public void setDepartureTime(Instant departureTime) { this.departureTime = departureTime; }
    public Instant getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(Instant arrivalTime) { this.arrivalTime = arrivalTime; }
    public Instant getReturnTime() { return returnTime; }
    public void setReturnTime(Instant returnTime) { this.returnTime = returnTime; }
    public FleetStatus getStatus() { return status; }
    public void setStatus(FleetStatus status) { this.status = status; }
    public double getMetalLoot() { return metalLoot; }
    public void setMetalLoot(double metalLoot) { this.metalLoot = metalLoot; }
    public double getCrystalLoot() { return crystalLoot; }
    public void setCrystalLoot(double crystalLoot) { this.crystalLoot = crystalLoot; }
    public double getGasLoot() { return gasLoot; }
    public void setGasLoot(double gasLoot) { this.gasLoot = gasLoot; }
}
```

- [ ] **Step 2: Create FleetShip entity**

```java
package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "fleet_ship")
public class FleetShip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fleet_id", nullable = false)
    private Long fleetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ship_type", nullable = false, length = 32)
    private ShipType shipType;

    @Column(nullable = false)
    private int quantity;

    public FleetShip() {}

    public FleetShip(Long fleetId, ShipType shipType, int quantity) {
        this.fleetId = fleetId;
        this.shipType = shipType;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFleetId() { return fleetId; }
    public void setFleetId(Long fleetId) { this.fleetId = fleetId; }
    public ShipType getShipType() { return shipType; }
    public void setShipType(ShipType shipType) { this.shipType = shipType; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
```

- [ ] **Step 3: Create CombatReport entity**

```java
package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "combat_report")
public class CombatReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attacker_id", nullable = false)
    private Long attackerId;

    @Column(name = "defender_id", nullable = false)
    private Long defenderId;

    @Column(name = "attacker_planet_id", nullable = false)
    private Long attackerPlanetId;

    @Column(name = "defender_planet_id", nullable = false)
    private Long defenderPlanetId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false, length = 16)
    private String result;

    @Column(name = "attacker_ships_before", columnDefinition = "jsonb", nullable = false)
    private String attackerShipsBefore;

    @Column(name = "defender_ships_before", columnDefinition = "jsonb", nullable = false)
    private String defenderShipsBefore;

    @Column(name = "attacker_ships_lost", columnDefinition = "jsonb", nullable = false)
    private String attackerShipsLost;

    @Column(name = "defender_ships_lost", columnDefinition = "jsonb", nullable = false)
    private String defenderShipsLost;

    @Column(name = "debris_metal", nullable = false)
    private double debrisMetal;

    @Column(name = "debris_crystal", nullable = false)
    private double debrisCrystal;

    @Column(name = "resources_looted", columnDefinition = "jsonb", nullable = false)
    private String resourcesLooted;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String rounds;

    public CombatReport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAttackerId() { return attackerId; }
    public void setAttackerId(Long attackerId) { this.attackerId = attackerId; }
    public Long getDefenderId() { return defenderId; }
    public void setDefenderId(Long defenderId) { this.defenderId = defenderId; }
    public Long getAttackerPlanetId() { return attackerPlanetId; }
    public void setAttackerPlanetId(Long attackerPlanetId) { this.attackerPlanetId = attackerPlanetId; }
    public Long getDefenderPlanetId() { return defenderPlanetId; }
    public void setDefenderPlanetId(Long defenderPlanetId) { this.defenderPlanetId = defenderPlanetId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getAttackerShipsBefore() { return attackerShipsBefore; }
    public void setAttackerShipsBefore(String attackerShipsBefore) { this.attackerShipsBefore = attackerShipsBefore; }
    public String getDefenderShipsBefore() { return defenderShipsBefore; }
    public void setDefenderShipsBefore(String defenderShipsBefore) { this.defenderShipsBefore = defenderShipsBefore; }
    public String getAttackerShipsLost() { return attackerShipsLost; }
    public void setAttackerShipsLost(String attackerShipsLost) { this.attackerShipsLost = attackerShipsLost; }
    public String getDefenderShipsLost() { return defenderShipsLost; }
    public void setDefenderShipsLost(String defenderShipsLost) { this.defenderShipsLost = defenderShipsLost; }
    public double getDebrisMetal() { return debrisMetal; }
    public void setDebrisMetal(double debrisMetal) { this.debrisMetal = debrisMetal; }
    public double getDebrisCrystal() { return debrisCrystal; }
    public void setDebrisCrystal(double debrisCrystal) { this.debrisCrystal = debrisCrystal; }
    public String getResourcesLooted() { return resourcesLooted; }
    public void setResourcesLooted(String resourcesLooted) { this.resourcesLooted = resourcesLooted; }
    public String getRounds() { return rounds; }
    public void setRounds(String rounds) { this.rounds = rounds; }
}
```

- [ ] **Step 4: Create DebrisField entity**

```java
package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "debris_field")
public class DebrisField {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planet_id", nullable = false, unique = true)
    private Long planetId;

    @Column(nullable = false)
    private double metal;

    @Column(nullable = false)
    private double crystal;

    public DebrisField() {}

    public DebrisField(Long planetId) {
        this.planetId = planetId;
        this.metal = 0;
        this.crystal = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanetId() { return planetId; }
    public void setPlanetId(Long planetId) { this.planetId = planetId; }
    public double getMetal() { return metal; }
    public void setMetal(double metal) { this.metal = metal; }
    public double getCrystal() { return crystal; }
    public void setCrystal(double crystal) { this.crystal = crystal; }
    public void addMetal(double amount) { this.metal += amount; }
    public void addCrystal(double amount) { this.crystal += amount; }
}
```

---

### Task 4: Repositories

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/FleetRepository.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/FleetShipRepository.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/CombatReportRepository.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/DebrisFieldRepository.java`

- [ ] **Step 1: Create FleetRepository**

```java
package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.Fleet;
import com.galaxyempire.game.domain.FleetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface FleetRepository extends JpaRepository<Fleet, Long> {
    List<Fleet> findByOriginPlanetId(Long originPlanetId);
    List<Fleet> findByPlayerId(Long playerId);
    List<Fleet> findByStatusAndArrivalTimeLessThanEqual(FleetStatus status, Instant now);
    List<Fleet> findByStatusAndReturnTimeLessThanEqual(FleetStatus status, Instant now);
}
```

- [ ] **Step 2: Create FleetShipRepository**

```java
package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.FleetShip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FleetShipRepository extends JpaRepository<FleetShip, Long> {
    List<FleetShip> findByFleetId(Long fleetId);
}
```

- [ ] **Step 3: Create CombatReportRepository**

```java
package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.CombatReport;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CombatReportRepository extends JpaRepository<CombatReport, Long> {
    @Query("SELECT r FROM CombatReport r WHERE r.attackerPlanetId = :planetId OR r.defenderPlanetId = :planetId ORDER BY r.timestamp DESC")
    List<CombatReport> findByPlanetId(@Param("planetId") Long planetId);
}
```

- [ ] **Step 4: Create DebrisFieldRepository**

```java
package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.DebrisField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DebrisFieldRepository extends JpaRepository<DebrisField, Long> {
    Optional<DebrisField> findByPlanetId(Long planetId);
}
```

---

### Task 5: GameBalancer — ship combat stats and travel time

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/GameBalancer.java`

- [ ] **Step 1: Add combat stats and travel time methods**

```java
// Add import:
import com.galaxyempire.game.domain.ShipType;

// Add methods after the ship cost methods:

public int getShipAttack(ShipType type) {
    return switch (type) {
        case LIGHT_FIGHTER -> 50;
        case HEAVY_FIGHTER -> 150;
        case CRUISER -> 400;
        case BATTLESHIP -> 1000;
        case SMALL_CARGO -> 5;
        case LARGE_CARGO -> 5;
        case COLONY_SHIP -> 50;
        case RECYCLER -> 1;
        case ESPIONAGE_PROBE -> 0;
    };
}

public int getShipShield(ShipType type) {
    return switch (type) {
        case LIGHT_FIGHTER -> 10;
        case HEAVY_FIGHTER -> 25;
        case CRUISER -> 50;
        case BATTLESHIP -> 200;
        case SMALL_CARGO -> 10;
        case LARGE_CARGO -> 25;
        case COLONY_SHIP -> 100;
        case RECYCLER -> 10;
        case ESPIONAGE_PROBE -> 0;
    };
}

public int getShipHull(ShipType type) {
    return switch (type) {
        case LIGHT_FIGHTER -> 400;
        case HEAVY_FIGHTER -> 1000;
        case CRUISER -> 2700;
        case BATTLESHIP -> 6000;
        case SMALL_CARGO -> 400;
        case LARGE_CARGO -> 1200;
        case COLONY_SHIP -> 3000;
        case RECYCLER -> 1600;
        case ESPIONAGE_PROBE -> 100;
    };
}

public int getShipSpeed(ShipType type) {
    return switch (type) {
        case LIGHT_FIGHTER -> 12500;
        case HEAVY_FIGHTER -> 10000;
        case CRUISER -> 15000;
        case BATTLESHIP -> 10000;
        case SMALL_CARGO -> 5000;
        case LARGE_CARGO -> 7500;
        case COLONY_SHIP -> 2500;
        case RECYCLER -> 2000;
        case ESPIONAGE_PROBE -> 10000000;
    };
}

public int getShipCargo(ShipType type) {
    return switch (type) {
        case LIGHT_FIGHTER -> 50;
        case HEAVY_FIGHTER -> 100;
        case CRUISER -> 800;
        case BATTLESHIP -> 1500;
        case SMALL_CARGO -> 5000;
        case LARGE_CARGO -> 25000;
        case COLONY_SHIP -> 7500;
        case RECYCLER -> 20000;
        case ESPIONAGE_PROBE -> 0;
    };
}

public Map<ShipType, Map<ShipType, Integer>> getRapidFire() {
    Map<ShipType, Map<ShipType, Integer>> rf = new HashMap<>();
    
    rf.put(ShipType.LIGHT_FIGHTER, Map.of(ShipType.ESPIONAGE_PROBE, 5));
    rf.put(ShipType.HEAVY_FIGHTER, Map.of(ShipType.ESPIONAGE_PROBE, 5, ShipType.SMALL_CARGO, 3));
    rf.put(ShipType.CRUISER, Map.of(ShipType.ESPIONAGE_PROBE, 5, ShipType.LIGHT_FIGHTER, 3));
    rf.put(ShipType.BATTLESHIP, Map.of(ShipType.ESPIONAGE_PROBE, 5, ShipType.HEAVY_FIGHTER, 3, ShipType.CRUISER, 2, ShipType.SMALL_CARGO, 5));
    
    return rf;
}

public int getFleetSpeed(ShipType type) {
    return getShipSpeed(type);
}

public int getTravelTimeSeconds(long distance) {
    return Math.max(10, (int) (distance / speed));
}
```

Also update the imports at the top of GameBalancer.java:
```java
import java.util.Map;
import java.util.HashMap;
```

---

### Task 6: CombatService

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/service/CombatService.java`

- [ ] **Step 1: Create CombatService**

```java
package com.galaxyempire.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CombatService {

    private final PlanetShipRepository planetShipRepository;
    private final FleetShipRepository fleetShipRepository;
    private final DebrisFieldRepository debrisFieldRepository;
    private final CombatReportRepository combatReportRepository;
    private final PlanetRepository planetRepository;
    private final GameBalancer gameBalancer;
    private final ObjectMapper objectMapper;

    public CombatService(PlanetShipRepository planetShipRepository,
                         FleetShipRepository fleetShipRepository,
                         DebrisFieldRepository debrisFieldRepository,
                         CombatReportRepository combatReportRepository,
                         PlanetRepository planetRepository,
                         GameBalancer gameBalancer,
                         ObjectMapper objectMapper) {
        this.planetShipRepository = planetShipRepository;
        this.debrisFieldRepository = debrisFieldRepository;
        this.combatReportRepository = combatReportRepository;
        this.planetRepository = planetRepository;
        this.gameBalancer = gameBalancer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CombatReport resolveCombat(Fleet fleet, List<FleetShip> attackerShips) {
        Long targetPlanetId = fleet.getTargetPlanetId();
        List<PlanetShip> defenderShips = planetShipRepository.findByPlanetId(targetPlanetId);

        Map<String, Integer> attackerBefore = shipsToMap(attackerShips);
        Map<String, Integer> defenderBefore = shipsToMapFromPlanetShips(defenderShips);

        List<FleetShip> attackerCurrent = deepCopyFleetShips(attackerShips);
        List<PlanetShip> defenderCurrent = new ArrayList<>(defenderShips);

        double debrisMetal = 0;
        double debrisCrystal = 0;
        List<Map<String, Object>> roundData = new ArrayList<>();

        int maxRounds = 6;

        for (int round = 0; round < maxRounds; round++) {
            if (attackerCurrent.isEmpty() || defenderCurrent.isEmpty()) break;

            Map<String, Integer> roundAttackerLosses = new HashMap<>();
            Map<String, Integer> roundDefenderLosses = new HashMap<>();

            // Attacker fires at defender
            for (FleetShip fs : new ArrayList<>(attackerCurrent)) {
                if (fs.getQuantity() <= 0) continue;
                fireShipGroup(fs.getShipType(), fs.getQuantity(), defenderCurrent,
                    roundDefenderLosses, true);
            }

            // Defender fires at attacker (only if they have ships)
            for (PlanetShip ps : new ArrayList<>(defenderCurrent)) {
                if (ps.getQuantity() <= 0) continue;
                ShipType shipType = ps.getShipType();
                int quantity = ps.getQuantity();
                // Create temporary FleetShip for the firing
                fireShipGroup(shipType, quantity, attackerCurrent, roundAttackerLosses, false);
            }

            // Clean up destroyed ships
            removeZeroQuantity(attackerCurrent);
            removeZeroQuantity(defenderCurrent);

            Map<String, Object> roundEntry = new HashMap<>();
            roundEntry.put("round", round + 1);
            roundEntry.put("attackerLosses", roundAttackerLosses);
            roundEntry.put("defenderLosses", roundDefenderLosses);
            roundData.add(roundEntry);
        }

        // Calculate total losses
        Map<String, Integer> attackerLost = new HashMap<>();
        Map<String, Integer> defenderLost = new HashMap<>();
        for (Map<String, Object> rd : roundData) {
            mergeLosses(attackerLost, (Map<String, Integer>) rd.get("attackerLosses"));
            mergeLosses(defenderLost, (Map<String, Integer>) rd.get("defenderLosses"));
        }

        // Debris: 30% of resources used to build destroyed ships
        for (Map.Entry<String, Integer> entry : attackerLost.entrySet()) {
            ShipType type = ShipType.valueOf(entry.getKey());
            int count = entry.getValue();
            debrisMetal += gameBalancer.getShipMetalCost(type) * 0.3 * count;
            debrisCrystal += gameBalancer.getShipCrystalCost(type) * 0.3 * count;
        }
        for (Map.Entry<String, Integer> entry : defenderLost.entrySet()) {
            ShipType type = ShipType.valueOf(entry.getKey());
            int count = entry.getValue();
            debrisMetal += gameBalancer.getShipMetalCost(type) * 0.3 * count;
            debrisCrystal += gameBalancer.getShipCrystalCost(type) * 0.3 * count;
        }

        // Determine result
        boolean attackerDefeated = attackerCurrent.isEmpty();
        boolean defenderDefeated = defenderCurrent.isEmpty();
        String result;
        double lootedMetal = 0, lootedCrystal = 0, lootedGas = 0;

        if (defenderDefeated && !attackerDefeated) {
            result = "ATTACKER_WIN";
            // Loot resources from target planet
            Planet targetPlanet = planetRepository.findById(targetPlanetId).orElse(null);
            if (targetPlanet != null) {
                long totalCargo = attackerCurrent.stream()
                    .mapToLong(fs -> (long) gameBalancer.getShipCargo(fs.getShipType()) * fs.getQuantity())
                    .sum();
                lootedMetal = Math.min(targetPlanet.getMetal(), totalCargo / 3);
                lootedCrystal = Math.min(targetPlanet.getCrystal(), (totalCargo - lootedMetal) / 2);
                lootedGas = Math.min(targetPlanet.getGas(), totalCargo - lootedMetal - lootedCrystal);

                targetPlanet.setMetal(targetPlanet.getMetal() - lootedMetal);
                targetPlanet.setCrystal(targetPlanet.getCrystal() - lootedCrystal);
                targetPlanet.setGas(targetPlanet.getGas() - lootedGas);
                planetRepository.save(targetPlanet);
            }
        } else if (attackerDefeated && !defenderDefeated) {
            result = "DEFENDER_WIN";
        } else {
            result = "DRAW";
        }

        // Update defender's planet_ships
        planetShipRepository.deleteAll(defenderShips);
        planetShipRepository.saveAll(defenderCurrent);

        // Update attacker's fleet_ships with survivors
        List<FleetShip> existingShips = fleetShipRepository.findByFleetId(fleet.getId());
        fleetShipRepository.deleteAll(existingShips);
        for (FleetShip survivor : attackerCurrent) {
            if (survivor.getQuantity() > 0) {
                fleetShipRepository.save(survivor);
            }
        }

        // Create/update debris field
        if (debrisMetal > 0 || debrisCrystal > 0) {
            DebrisField df = debrisFieldRepository.findByPlanetId(targetPlanetId)
                .orElseGet(() -> new DebrisField(targetPlanetId));
            df.addMetal(debrisMetal);
            df.addCrystal(debrisCrystal);
            debrisFieldRepository.save(df);
        }

        // Update fleet with loot
        fleet.setMetalLoot(lootedMetal);
        fleet.setCrystalLoot(lootedCrystal);
        fleet.setGasLoot(lootedGas);

        // Get defender player ID from target planet
        Planet targetPlanet = planetRepository.findById(targetPlanetId).orElse(null);
        long defenderPlayerId = targetPlanet != null ? targetPlanet.getPlayerId() : 0L;

        // Create combat report
        CombatReport report = new CombatReport();
        report.setAttackerId(fleet.getPlayerId());
        report.setDefenderId(defenderPlayerId);
        report.setAttackerPlanetId(fleet.getOriginPlanetId());
        report.setDefenderPlanetId(targetPlanetId);
        report.setTimestamp(Instant.now());
        report.setResult(result);
        report.setAttackerShipsBefore(toJson(attackerBefore));
        report.setDefenderShipsBefore(toJson(defenderBefore));
        report.setAttackerShipsLost(toJson(attackerLost));
        report.setDefenderShipsLost(toJson(defenderLost));
        report.setDebrisMetal(debrisMetal);
        report.setDebrisCrystal(debrisCrystal);
        report.setResourcesLooted(toJson(Map.of("metal", lootedMetal, "crystal", lootedCrystal, "gas", lootedGas)));
        report.setRounds(toJson(roundData));
        return combatReportRepository.save(report);
    }

    private void fireShipGroup(ShipType firerType, int quantity, List<?> targets,
                                 Map<String, Integer> losses, boolean isAttacker) {
        Map<ShipType, Map<ShipType, Integer>> rapidFire = gameBalancer.getRapidFire();
        Map<ShipType, Integer> rfForFirer = rapidFire.getOrDefault(firerType, Map.of());
        int attack = gameBalancer.getShipAttack(firerType);
        Random rand = new Random();

        for (int i = 0; i < quantity; i++) {
            boolean canFire = true;
            while (canFire) {
                ShipType targetType = pickRandomTarget(targets, rand);
                if (targetType == null) { canFire = false; break; }

                int shield = gameBalancer.getShipShield(targetType);
                int hull = gameBalancer.getShipHull(targetType);
                int damage = Math.max(0, attack - shield);

                if (damage > 0) {
                    boolean destroyed = applyDamage(targets, targetType, damage, losses, isAttacker);
                    if (destroyed) {
                        int rfValue = rfForFirer.getOrDefault(targetType, 0);
                        if (rfValue > 0) {
                            int roll = rand.nextInt(rfValue) + 1;
                            canFire = roll > 1;
                        } else {
                            canFire = false;
                        }
                    } else {
                        canFire = false;
                    }
                } else {
                    canFire = false;
                }
            }
        }
    }

    private ShipType pickRandomTarget(List<?> targets, Random rand) {
        List<ShipType> available = new ArrayList<>();
        for (Object obj : targets) {
            if (obj instanceof FleetShip) {
                FleetShip fs = (FleetShip) obj;
                if (fs.getQuantity() > 0) available.add(fs.getShipType());
            } else if (obj instanceof PlanetShip) {
                PlanetShip ps = (PlanetShip) obj;
                if (ps.getQuantity() > 0) available.add(ps.getShipType());
            }
        }
        if (available.isEmpty()) return null;
        return available.get(rand.nextInt(available.size()));
    }

    private boolean applyDamage(List<?> targets, ShipType targetType, int damage,
                                 Map<String, Integer> losses, boolean isAttacker) {
        for (Object obj : targets) {
            int targetHull = gameBalancer.getShipHull(targetType);
            if (obj instanceof FleetShip) {
                FleetShip fs = (FleetShip) obj;
                if (fs.getShipType() == targetType && fs.getQuantity() > 0) {
                    int shipsDestroyed = Math.max(1, damage / targetHull);
                    shipsDestroyed = Math.min(shipsDestroyed, fs.getQuantity());
                    fs.setQuantity(fs.getQuantity() - shipsDestroyed);
                    losses.merge(targetType.name(), shipsDestroyed, Integer::sum);
                    return true;
                }
            } else if (obj instanceof PlanetShip) {
                PlanetShip ps = (PlanetShip) obj;
                if (ps.getShipType() == targetType && ps.getQuantity() > 0) {
                    int shipsDestroyed = Math.max(1, damage / targetHull);
                    shipsDestroyed = Math.min(shipsDestroyed, ps.getQuantity());
                    ps.setQuantity(ps.getQuantity() - shipsDestroyed);
                    losses.merge(targetType.name(), shipsDestroyed, Integer::sum);
                    return true;
                }
            }
        }
        return false;
    }

    private void removeZeroQuantity(List<?> list) {
        list.removeIf(obj -> {
            if (obj instanceof FleetShip) return ((FleetShip) obj).getQuantity() <= 0;
            if (obj instanceof PlanetShip) return ((PlanetShip) obj).getQuantity() <= 0;
            return false;
        });
    }

    private List<FleetShip> deepCopyFleetShips(List<FleetShip> original) {
        return original.stream()
            .map(fs -> new FleetShip(fs.getFleetId(), fs.getShipType(), fs.getQuantity()))
            .collect(Collectors.toList());
    }

    private Map<String, Integer> shipsToMap(List<FleetShip> ships) {
        Map<String, Integer> map = new HashMap<>();
        for (FleetShip fs : ships) {
            map.put(fs.getShipType().name(), fs.getQuantity());
        }
        return map;
    }

    private Map<String, Integer> shipsToMapFromPlanetShips(List<PlanetShip> ships) {
        Map<String, Integer> map = new HashMap<>();
        for (PlanetShip ps : ships) {
            map.put(ps.getShipType().name(), ps.getQuantity());
        }
        return map;
    }

    private void mergeLosses(Map<String, Integer> total, Map<String, Integer> round) {
        for (Map.Entry<String, Integer> e : round.entrySet()) {
            total.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
```

---

### Task 7: FleetService

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/service/FleetService.java`

- [ ] **Step 1: Create FleetService**

```java
package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class FleetService {

    private final FleetRepository fleetRepository;
    private final FleetShipRepository fleetShipRepository;
    private final CombatReportRepository combatReportRepository;
    private final DebrisFieldRepository debrisFieldRepository;
    private final PlanetShipRepository planetShipRepository;
    private final PlanetRepository planetRepository;
    private final CombatService combatService;
    private final GameBalancer gameBalancer;

    public FleetService(FleetRepository fleetRepository,
                        FleetShipRepository fleetShipRepository,
                        CombatReportRepository combatReportRepository,
                        DebrisFieldRepository debrisFieldRepository,
                        PlanetShipRepository planetShipRepository,
                        PlanetRepository planetRepository,
                        CombatService combatService,
                        GameBalancer gameBalancer) {
        this.fleetRepository = fleetRepository;
        this.fleetShipRepository = fleetShipRepository;
        this.combatReportRepository = combatReportRepository;
        this.debrisFieldRepository = debrisFieldRepository;
        this.planetShipRepository = planetShipRepository;
        this.planetRepository = planetRepository;
        this.combatService = combatService;
        this.gameBalancer = gameBalancer;
    }

    @Transactional
    public Map<String, Object> launchFleet(Long originPlanetId, Long targetPlanetId,
                                            FleetMission mission, Map<String, Integer> ships,
                                            Long playerId) {
        // Validate ownership
        Planet origin = planetRepository.findById(originPlanetId)
            .orElseThrow(() -> new IllegalArgumentException("Origin planet not found"));
        if (!origin.getPlayerId().equals(playerId)) {
            throw new IllegalArgumentException("Origin planet does not belong to player");
        }
        if (originPlanetId.equals(targetPlanetId)) {
            throw new IllegalArgumentException("Target must be a different planet");
        }
        if (ships == null || ships.isEmpty()) {
            throw new IllegalArgumentException("Must send at least one ship");
        }

        // For deploy, validate target is also owned by player
        if (mission == FleetMission.DEPLOY) {
            Planet target = planetRepository.findById(targetPlanetId)
                .orElseThrow(() -> new IllegalArgumentException("Target planet not found"));
            if (!target.getPlayerId().equals(playerId)) {
                throw new IllegalArgumentException("Can only deploy to own planets");
            }
        }

        // Deduct ships from origin planet
        List<PlanetShip> originShips = planetShipRepository.findByPlanetId(originPlanetId);
        List<FleetShip> fleetShips = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ships.entrySet()) {
            ShipType type = ShipType.valueOf(entry.getKey().toUpperCase());
            int quantity = entry.getValue();
            PlanetShip ps = originShips.stream()
                .filter(s -> s.getShipType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No " + type + " at origin planet"));
            if (ps.getQuantity() < quantity) {
                throw new IllegalArgumentException("Insufficient " + type + " at origin planet");
            }
            ps.setQuantity(ps.getQuantity() - quantity);
            planetShipRepository.save(ps);
        }

        // Calculate travel time (simple: use speed of slowest ship)
        int slowestSpeed = ships.keySet().stream()
            .mapToInt(k -> gameBalancer.getShipSpeed(ShipType.valueOf(k)))
            .min()
            .orElse(100);
        int travelTimeSecs = gameBalancer.getTravelTimeSeconds(1); // distance=1 for now
        long roundTripSecs = travelTimeSecs * 2L;

        // Create fleet
        Fleet fleet = new Fleet();
        fleet.setOriginPlanetId(originPlanetId);
        fleet.setTargetPlanetId(targetPlanetId);
        fleet.setPlayerId(playerId);
        fleet.setMission(mission);
        fleet.setDepartureTime(Instant.now());
        fleet.setArrivalTime(Instant.now().plusSeconds(travelTimeSecs));
        fleet.setReturnTime(null);
        fleet.setStatus(FleetStatus.EN_ROUTE);
        fleet = fleetRepository.save(fleet);

        // Create fleet ships
        for (Map.Entry<String, Integer> entry : ships.entrySet()) {
            ShipType type = ShipType.valueOf(entry.getKey().toUpperCase());
            int quantity = entry.getValue();
            fleetShipRepository.save(new FleetShip(fleet.getId(), type, quantity));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fleetId", fleet.getId());
        result.put("mission", mission.name());
        result.put("arrivalTime", fleet.getArrivalTime().toString());
        result.put("travelTimeSeconds", travelTimeSecs);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFleetDetail(Long fleetId) {
        Fleet fleet = fleetRepository.findById(fleetId)
            .orElseThrow(() -> new IllegalArgumentException("Fleet not found"));
        List<FleetShip> ships = fleetShipRepository.findByFleetId(fleetId);
        Map<String, Object> result = new HashMap<>();
        result.put("fleet", fleet);
        Map<String, Integer> shipMap = new HashMap<>();
        for (FleetShip fs : ships) {
            shipMap.put(fs.getShipType().name(), fs.getQuantity());
        }
        result.put("ships", shipMap);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Fleet> getPlanetFleets(Long planetId) {
        return fleetRepository.findByOriginPlanetId(planetId);
    }

    @Transactional
    public void recallFleet(Long fleetId, Long playerId) {
        Fleet fleet = fleetRepository.findById(fleetId)
            .orElseThrow(() -> new IllegalArgumentException("Fleet not found"));
        if (!fleet.getPlayerId().equals(playerId)) {
            throw new IllegalArgumentException("Fleet does not belong to player");
        }
        if (fleet.getStatus() != FleetStatus.EN_ROUTE) {
            throw new IllegalArgumentException("Fleet cannot be recalled");
        }
        fleet.setStatus(FleetStatus.RETURNING);
        long travelTime = java.time.Duration.between(fleet.getDepartureTime(), fleet.getArrivalTime()).getSeconds();
        fleet.setReturnTime(Instant.now().plusSeconds(travelTime));
        fleetRepository.save(fleet);
    }

    @Transactional
    public void processArrivals(Instant now) {
        List<Fleet> arrivals = fleetRepository
            .findByStatusAndArrivalTimeLessThanEqual(FleetStatus.EN_ROUTE, now);
        for (Fleet fleet : arrivals) {
            try {
                List<FleetShip> ships = fleetShipRepository.findByFleetId(fleet.getId());
                if (ships.isEmpty()) {
                    fleet.setStatus(FleetStatus.RETURNING);
                    fleet.setReturnTime(Instant.now());
                    fleetRepository.save(fleet);
                    continue;
                }

                if (fleet.getMission() == FleetMission.ATTACK) {
                    CombatReport report = combatService.resolveCombat(fleet, ships);
                    // Update fleet with loot
                    // After combat, surviving attacker ships return
                    List<FleetShip> currentShips = fleetShipRepository.findByFleetId(fleet.getId());
                    boolean anySurvivors = currentShips.stream().anyMatch(fs -> fs.getQuantity() > 0);
                    if (anySurvivors) {
                        fleet.setStatus(FleetStatus.RETURNING);
                        long travelTime = java.time.Duration.between(fleet.getDepartureTime(), fleet.getArrivalTime()).getSeconds();
                        fleet.setReturnTime(Instant.now().plusSeconds(travelTime));
                    } else {
                        fleet.setStatus(FleetStatus.ARRIVED);
                        fleet.setReturnTime(null);
                    }
                    fleetRepository.save(fleet);
                } else if (fleet.getMission() == FleetMission.DEPLOY) {
                    // Transfer ships to target planet
                    for (FleetShip fs : ships) {
                        PlanetShip ps = planetShipRepository
                            .findByPlanetIdAndShipType(fleet.getTargetPlanetId(), fs.getShipType())
                            .orElseGet(() -> planetShipRepository.save(
                                new PlanetShip(fleet.getTargetPlanetId(), fs.getShipType())));
                        ps.addQuantity(fs.getQuantity());
                        planetShipRepository.save(ps);
                    }
                    fleet.setStatus(FleetStatus.ARRIVED);
                    fleet.setReturnTime(null);
                    fleetRepository.save(fleet);
                }
            } catch (Exception e) {
                System.err.println("Failed to process fleet " + fleet.getId() + ": " + e.getMessage());
                fleet.setStatus(FleetStatus.ARRIVED);
                fleetRepository.save(fleet);
            }
        }
    }

    @Transactional
    public void processReturns(Instant now) {
        List<Fleet> returns = fleetRepository
            .findByStatusAndReturnTimeLessThanEqual(FleetStatus.RETURNING, now);
        for (Fleet fleet : returns) {
            try {
                // Transfer surviving ships back to origin planet
                List<FleetShip> ships = fleetShipRepository.findByFleetId(fleet.getId());
                for (FleetShip fs : ships) {
                    if (fs.getQuantity() > 0) {
                        PlanetShip ps = planetShipRepository
                            .findByPlanetIdAndShipType(fleet.getOriginPlanetId(), fs.getShipType())
                            .orElseGet(() -> planetShipRepository.save(
                                new PlanetShip(fleet.getOriginPlanetId(), fs.getShipType())));
                        ps.addQuantity(fs.getQuantity());
                        planetShipRepository.save(ps);

                        // Add looted resources to origin planet
                        if (fleet.getMetalLoot() > 0 || fleet.getCrystalLoot() > 0 || fleet.getGasLoot() > 0) {
                            Planet origin = planetRepository.findById(fleet.getOriginPlanetId()).orElse(null);
                            if (origin != null) {
                                origin.setMetal(origin.getMetal() + fleet.getMetalLoot());
                                origin.setCrystal(origin.getCrystal() + fleet.getCrystalLoot());
                                origin.setGas(origin.getGas() + fleet.getGasLoot());
                                planetRepository.save(origin);
                            }
                        }
                    }
                }
                fleet.setStatus(FleetStatus.ARRIVED);
                fleet.setReturnTime(null);
                fleetRepository.save(fleet);
            } catch (Exception e) {
                System.err.println("Failed to process return " + fleet.getId() + ": " + e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<CombatReport> getPlanetCombatReports(Long planetId) {
        return combatReportRepository.findByPlanetId(planetId);
    }

    @Transactional(readOnly = true)
    public CombatReport getCombatReport(Long reportId) {
        return combatReportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Combat report not found"));
    }

    @Transactional(readOnly = true)
    public Optional<DebrisField> getDebrisField(Long planetId) {
        return debrisFieldRepository.findByPlanetId(planetId);
    }
}
```

---

### Task 8: FleetController

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/web/FleetController.java`

- [ ] **Step 1: Create FleetController**

```java
package com.galaxyempire.game.web;

import com.galaxyempire.game.domain.FleetMission;
import com.galaxyempire.game.service.FleetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class FleetController {

    private final FleetService fleetService;

    public FleetController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    @PostMapping("/planets/{planetId}/fleet")
    public ResponseEntity<?> launchFleet(@PathVariable Long planetId,
                                          @RequestBody Map<String, Object> body,
                                          @RequestHeader("X-Player-Id") Long playerId) {
        try {
            Long targetPlanetId = Long.valueOf(body.get("targetPlanetId").toString());
            FleetMission mission = FleetMission.valueOf(body.get("mission").toString().toUpperCase());
            Map<String, Integer> ships = (Map<String, Integer>) body.get("ships");
            var result = fleetService.launchFleet(planetId, targetPlanetId, mission, ships, playerId);
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
        var df = fleetService.getDebrisField(planetId);
        return ResponseEntity.ok(df.orElse(Map.of("metal", 0, "crystal", 0)));
    }
}
```

---

### Task 9: GameLoopService — fleet processing

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/GameLoopService.java`

- [ ] **Step 1: Add FleetService dependency and calls**

Add field and constructor parameter:
```java
private final FleetService fleetService;
```

In constructor:
```java
FleetService fleetService
// set this.fleetService = fleetService;
```

In processGameLoop(), add after processShipyardCompletions():
```java
fleetService.processArrivals(Instant.now());
fleetService.processReturns(Instant.now());
```

---

### Task 10: Frontend models and service methods

**Files:**
- Modify: `frontend/src/app/core/models/models.ts`
- Modify: `frontend/src/app/core/services/game.service.ts`

- [ ] **Step 1: Add fleet interfaces to models.ts**

```typescript
export interface Fleet {
  id: number;
  originPlanetId: number;
  targetPlanetId: number;
  playerId: number;
  mission: string;
  departureTime: string;
  arrivalTime: string;
  returnTime: string | null;
  status: string;
  metalLoot: number;
  crystalLoot: number;
  gasLoot: number;
}

export interface FleetShipGroup {
  shipType: string;
  quantity: number;
}

export interface CombatReport {
  id: number;
  attackerId: number;
  defenderId: number;
  attackerPlanetId: number;
  defenderPlanetId: number;
  timestamp: string;
  result: string;
  attackerShipsBefore: string;
  defenderShipsBefore: string;
  attackerShipsLost: string;
  defenderShipsLost: string;
  debrisMetal: number;
  debrisCrystal: number;
  resourcesLooted: string;
}

export interface DebrisField {
  metal: number;
  crystal: number;
}
```

- [ ] **Step 2: Add fleet API methods to game.service.ts**

```typescript
import { Fleet, CombatReport, DebrisField } from '../models/models';

launchFleet(planetId: number, targetPlanetId: number, mission: string, ships: {[key: string]: number}): Observable<any> {
  return this.http.post(`${environment.apiUrl}/game/planets/${planetId}/fleet`, {
    targetPlanetId, mission, ships
  });
}

getPlanetFleets(planetId: number): Observable<Fleet[]> {
  return this.http.get<Fleet[]>(`${environment.apiUrl}/game/planets/${planetId}/fleet`);
}

getFleetDetail(fleetId: number): Observable<any> {
  return this.http.get(`${environment.apiUrl}/game/fleet/${fleetId}`);
}

recallFleet(fleetId: number): Observable<any> {
  return this.http.post(`${environment.apiUrl}/game/fleet/${fleetId}/recall`, {});
}

getCombatReports(planetId: number): Observable<CombatReport[]> {
  return this.http.get<CombatReport[]>(`${environment.apiUrl}/game/planets/${planetId}/combat-reports`);
}

getDebrisField(planetId: number): Observable<DebrisField> {
  return this.http.get<DebrisField>(`${environment.apiUrl}/game/planets/${planetId}/debris`);
}
```

---

### Task 11: Frontend FleetComponent

**Files:**
- Create: `frontend/src/app/fleet/fleet.component.ts`

- [ ] **Step 1: Create FleetComponent**

```typescript
import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GameService } from '../core/services/game.service';
import { WebSocketService } from '../core/services/web-socket.service';
import { PlanetShip } from '../core/models/models';

@Component({
  selector: 'app-fleet',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="fleet-container">
      <h2>Fleet Command</h2>

      <div class="section">
        <h3>Your Ships at This Planet</h3>
        <div class="ship-list" *ngIf="planetShips.length > 0">
          <div *ngFor="let ship of planetShips" class="ship-row">
            <span>{{ getDisplayName(ship.shipType) }}: {{ ship.quantity }}</span>
          </div>
        </div>
        <div *ngIf="planetShips.length === 0" class="empty">No ships at this planet.</div>
      </div>

      <div class="section">
        <h3>Launch Fleet</h3>
        <div class="launch-form">
          <div class="form-row">
            <label>Target Planet ID:</label>
            <input type="number" [(ngModel)]="targetPlanetId" min="1">
          </div>
          <div class="form-row">
            <label>Mission:</label>
            <select [(ngModel)]="mission">
              <option value="ATTACK">Attack</option>
              <option value="DEPLOY">Deploy</option>
            </select>
          </div>
          <div class="form-row" *ngFor="let ship of planetShips">
            <label>{{ getDisplayName(ship.shipType) }} ({{ ship.quantity }} available):</label>
            <input type="number" [(ngModel)]="shipQuantities[ship.shipType]" min="0" [max]="ship.quantity" value="0">
          </div>
          <button (click)="launchFleet()" [disabled]="!targetPlanetId">Launch</button>
          <div *ngIf="launchError" class="error">{{ launchError }}</div>
        </div>
      </div>

      <div class="section">
        <h3>Active Fleets</h3>
        <div *ngFor="let fleet of activeFleets" class="fleet-card" [class.en-route]="fleet.status === 'EN_ROUTE'">
          <div class="fleet-mission">{{ fleet.mission }} → Planet {{ fleet.targetPlanetId }}</div>
          <div class="fleet-status">{{ fleet.status }}</div>
          <div class="fleet-eta" *ngIf="fleet.status === 'EN_ROUTE'">Arrives: {{ fleet.arrivalTime | date:'short' }}</div>
          <button *ngIf="fleet.status === 'EN_ROUTE'" (click)="recallFleet(fleet.id)">Recall</button>
        </div>
        <div *ngIf="activeFleets.length === 0" class="empty">No active fleets.</div>
      </div>

      <div class="section">
        <h3>Debris Field</h3>
        <div *ngIf="debrisField">
          <span>Metal: {{ debrisField.metal.toLocaleString() }}</span>
          <span>Crystal: {{ debrisField.crystal.toLocaleString() }}</span>
        </div>
        <div *ngIf="!debrisField || (debrisField.metal === 0 && debrisField.crystal === 0)" class="empty">
          No debris field at this planet.
        </div>
      </div>
    </div>
  `,
  styles: [`
    .fleet-container { padding: 20px; color: #ccc; }
    .section { background: #1a1a2e; border: 1px solid #333; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
    h3 { color: #ffd700; margin: 0 0 12px 0; font-size: 14px; }
    .ship-list { display: flex; flex-wrap: wrap; gap: 8px; }
    .ship-row { font-size: 13px; color: #ccc; }
    .empty { color: #666; font-style: italic; font-size: 13px; }
    .launch-form { display: flex; flex-direction: column; gap: 8px; }
    .form-row { display: flex; gap: 8px; align-items: center; }
    .form-row label { min-width: 200px; font-size: 13px; }
    .form-row input, .form-row select { padding: 4px; background: #222; border: 1px solid #444; color: #fff; border-radius: 4px; width: 80px; }
    button { padding: 6px 16px; background: #4a9; color: #fff; border: none; border-radius: 4px; cursor: pointer; margin-top: 8px; }
    button:hover { background: #5ba; }
    button:disabled { opacity: 0.5; cursor: not-allowed; }
    .fleet-card { background: #122; border: 1px solid #4a9; border-radius: 6px; padding: 12px; margin-bottom: 8px; }
    .fleet-card.en-route { border-color: #fa0; }
    .fleet-mission { font-size: 14px; color: #fff; font-weight: bold; }
    .fleet-status { font-size: 12px; color: #888; }
    .fleet-eta { font-size: 12px; color: #4a9; }
    .error { color: #f44; font-size: 12px; margin-top: 4px; }
  `]
})
export class FleetComponent implements OnInit, OnDestroy {
  planetId = 1;
  planetShips: PlanetShip[] = [];
  activeFleets: any[] = [];
  debrisField: any = null;
  targetPlanetId: number | null = null;
  mission: string = 'ATTACK';
  shipQuantities: { [key: string]: number } = {};
  launchError: string | null = null;

  constructor(
    private gameService: GameService,
    private ws: WebSocketService
  ) {}

  ngOnInit() {
    this.loadData();
    this.ws.connect();
    this.ws.subscribe('/topic/planet/*', () => this.loadData());
  }

  ngOnDestroy() {}

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
  }

  launchFleet() {
    if (!this.targetPlanetId) return;
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
    this.gameService.launchFleet(this.planetId, this.targetPlanetId, this.mission, ships).subscribe({
      next: () => this.loadData(),
      error: (err) => this.launchError = err.error?.error || 'Launch failed'
    });
  }

  recallFleet(fleetId: number) {
    this.gameService.recallFleet(fleetId).subscribe(() => this.loadData());
  }

  getDisplayName(name: string): string {
    return name.split('_').map(w => w.charAt(0) + w.slice(1).toLowerCase()).join(' ');
  }
}
```

---

### Task 12: Frontend routes and navigation

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/app.component.ts`

- [ ] **Step 1: Add fleet route**

In `app.routes.ts`, add:
```typescript
{ path: 'fleet', loadComponent: () => import('./fleet/fleet.component').then(m => m.FleetComponent), canActivate: [AuthGuard] },
```

- [ ] **Step 2: Add nav link**

In `app.component.ts`, add after Shipyard:
```html
<a routerLink="/fleet" routerLinkActive="active">Fleet</a>
```
