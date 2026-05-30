# Phase 2: Ship System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ship building system with Shipyard, 9 ship types, shipyard queue, and shipyard UI.

**Architecture:** Flyway migration → ShipType enum → PlanetShip + ShipyardQueue entities → GameBalancer ship formulas → ShipyardService → ShipyardController + queue processing in GameLoop → frontend shipyard view.

**Tech Stack:** Spring Boot 3.4, Java 21, PostgreSQL 16, Flyway, Angular 19

---

### Task 1: Database migration V5 (planet_ship + shipyard_queue)

**Files:**
- Create: `backend/game-service/src/main/resources/db/migration/V5__create_shipyard.sql`

- [ ] **Step 1: Create the migration**

```sql
CREATE TABLE planet_ship (
    id BIGSERIAL PRIMARY KEY,
    planet_id BIGINT NOT NULL,
    ship_type VARCHAR(32) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    UNIQUE (planet_id, ship_type)
);

CREATE TABLE shipyard_queue (
    id BIGSERIAL PRIMARY KEY,
    planet_id BIGINT NOT NULL,
    ship_type VARCHAR(32) NOT NULL,
    quantity INT NOT NULL,
    built_quantity INT NOT NULL DEFAULT 0,
    metal_cost DOUBLE PRECISION NOT NULL,
    crystal_cost DOUBLE PRECISION NOT NULL,
    gas_cost DOUBLE PRECISION NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completes_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE
);
```

- [ ] **Step 2: Build to verify Flyway picks it up**

Run: `cd backend && mvn -pl game-service -am compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 2: ShipType enum

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/ShipType.java`

- [ ] **Step 1: Create the enum**

```java
package com.galaxyempire.game.domain;

public enum ShipType {
    LIGHT_FIGHTER,
    HEAVY_FIGHTER,
    CRUISER,
    BATTLESHIP,
    SMALL_CARGO,
    LARGE_CARGO,
    COLONY_SHIP,
    RECYCLER,
    ESPIONAGE_PROBE
}
```

---

### Task 3: JPA entities and repositories

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/PlanetShip.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/ShipyardQueue.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/PlanetShipRepository.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/ShipyardQueueRepository.java`

- [ ] **Step 1: Create PlanetShip entity**

```java
package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "planet_ship", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"planet_id", "ship_type"})
})
public class PlanetShip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planet_id", nullable = false)
    private Long planetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ship_type", nullable = false, length = 32)
    private ShipType shipType;

    @Column(nullable = false)
    private int quantity = 0;

    public PlanetShip() {}

    public PlanetShip(Long planetId, ShipType shipType) {
        this.planetId = planetId;
        this.shipType = shipType;
        this.quantity = 0;
    }

    public Long getId() { return id; }
    public Long getPlanetId() { return planetId; }
    public ShipType getShipType() { return shipType; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void addQuantity(int amount) { this.quantity += amount; }
}
```

- [ ] **Step 2: Create ShipyardQueue entity**

```java
package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "shipyard_queue")
public class ShipyardQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planet_id", nullable = false)
    private Long planetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ship_type", nullable = false, length = 32)
    private ShipType shipType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "built_quantity", nullable = false)
    private int builtQuantity = 0;

    @Column(name = "metal_cost", nullable = false)
    private double metalCost;

    @Column(name = "crystal_cost", nullable = false)
    private double crystalCost;

    @Column(name = "gas_cost", nullable = false)
    private double gasCost;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completes_at", nullable = false)
    private Instant completesAt;

    @Column(nullable = false)
    private boolean completed = false;

    public ShipyardQueue() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanetId() { return planetId; }
    public void setPlanetId(Long planetId) { this.planetId = planetId; }
    public ShipType getShipType() { return shipType; }
    public void setShipType(ShipType shipType) { this.shipType = shipType; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getBuiltQuantity() { return builtQuantity; }
    public void setBuiltQuantity(int builtQuantity) { this.builtQuantity = builtQuantity; }
    public double getMetalCost() { return metalCost; }
    public void setMetalCost(double metalCost) { this.metalCost = metalCost; }
    public double getCrystalCost() { return crystalCost; }
    public void setCrystalCost(double crystalCost) { this.crystalCost = crystalCost; }
    public double getGasCost() { return gasCost; }
    public void setGasCost(double gasCost) { this.gasCost = gasCost; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletesAt() { return completesAt; }
    public void setCompletesAt(Instant completesAt) { this.completesAt = completesAt; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
```

- [ ] **Step 3: Create repositories**

```java
// PlanetShipRepository.java
package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.PlanetShip;
import com.galaxyempire.game.domain.ShipType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlanetShipRepository extends JpaRepository<PlanetShip, Long> {
    List<PlanetShip> findByPlanetId(Long planetId);
    Optional<PlanetShip> findByPlanetIdAndShipType(Long planetId, ShipType shipType);
}
```

```java
// ShipyardQueueRepository.java
package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.ShipyardQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface ShipyardQueueRepository extends JpaRepository<ShipyardQueue, Long> {
    List<ShipyardQueue> findByPlanetIdAndCompletedFalseOrderByStartedAt(Long planetId);
    List<ShipyardQueue> findByCompletedFalseAndCompletesAtLessThanEqual(Instant now);
}
```

---

### Task 4: GameBalancer - ship formulas

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/GameBalancer.java`

- [ ] **Step 1: Add ship cost and time methods**

```java
// Ship costs (per ship)
public double getShipMetalCost(ShipType type) {
    return switch (type) {
        case LIGHT_FIGHTER -> 500;
        case HEAVY_FIGHTER -> 2500;
        case CRUISER -> 5000;
        case BATTLESHIP -> 15000;
        case SMALL_CARGO -> 1000;
        case LARGE_CARGO -> 3000;
        case COLONY_SHIP -> 5000;
        case RECYCLER -> 2000;
        case ESPIONAGE_PROBE -> 100;
    } * speed;
}

public double getShipCrystalCost(ShipType type) {
    return switch (type) {
        case LIGHT_FIGHTER -> 100;
        case HEAVY_FIGHTER -> 500;
        case CRUISER -> 2000;
        case BATTLESHIP -> 5000;
        case SMALL_CARGO -> 500;
        case LARGE_CARGO -> 1500;
        case COLONY_SHIP -> 2500;
        case RECYCLER -> 1000;
        case ESPIONAGE_PROBE -> 50;
    } * speed;
}

public double getShipGasCost(ShipType type) {
    return switch (type) {
        case CRUISER -> 1000;
        case BATTLESHIP -> 3000;
        case COLONY_SHIP -> 5000;
        case RECYCLER -> 500;
        default -> 0;
    } * speed;
}

public int getShipBuildTimeSeconds(ShipType type, double shipyardLevel, double naniteLevel) {
    int base = switch (type) {
        case LIGHT_FIGHTER -> 120;
        case HEAVY_FIGHTER -> 360;
        case CRUISER -> 1200;
        case BATTLESHIP -> 3600;
        case SMALL_CARGO -> 240;
        case LARGE_CARGO -> 600;
        case COLONY_SHIP -> 2400;
        case RECYCLER -> 600;
        case ESPIONAGE_PROBE -> 30;
    };
    return (int) Math.ceil(base * speed / (1 + shipyardLevel + naniteLevel));
}
```

- [ ] **Step 2: Add shipyard level requirement method**

```java
public int getRequiredShipyardLevel(ShipType type) {
    return switch (type) {
        case LIGHT_FIGHTER, ESPIONAGE_PROBE -> 1;
        case SMALL_CARGO -> 2;
        case RECYCLER, HEAVY_FIGHTER -> 3;
        case LARGE_CARGO -> 4;
        case CRUISER, COLONY_SHIP -> 5;
        case BATTLESHIP -> 7;
    };
}
```

---

### Task 5: ShipyardService

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/service/ShipyardService.java`

- [ ] **Step 1: Create ShipyardService**

```java
package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ShipyardService {

    private final PlanetShipRepository planetShipRepository;
    private final ShipyardQueueRepository shipyardQueueRepository;
    private final BuildingRepository buildingRepository;
    private final PlanetRepository planetRepository;
    private final GameBalancer gameBalancer;

    public ShipyardService(PlanetShipRepository planetShipRepository,
                           ShipyardQueueRepository shipyardQueueRepository,
                           BuildingRepository buildingRepository,
                           PlanetRepository planetRepository,
                           GameBalancer gameBalancer) {
        this.planetShipRepository = planetShipRepository;
        this.shipyardQueueRepository = shipyardQueueRepository;
        this.buildingRepository = buildingRepository;
        this.planetRepository = planetRepository;
        this.gameBalancer = gameBalancer;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getShipTypes(Long planetId) {
        int shipyardLevel = buildingRepository
            .findByPlanetIdAndGridPosition(planetId, 9)
            .map(Building::getLevel)
            .orElse(0);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ShipType type : ShipType.values()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("shipType", type.name());
            entry.put("metalCost", gameBalancer.getShipMetalCost(type));
            entry.put("crystalCost", gameBalancer.getShipCrystalCost(type));
            entry.put("gasCost", gameBalancer.getShipGasCost(type));
            entry.put("timeSeconds", gameBalancer.getShipBuildTimeSeconds(type, shipyardLevel, 0));
            entry.put("requiredShipyardLevel", gameBalancer.getRequiredShipyardLevel(type));
            entry.put("available", shipyardLevel >= gameBalancer.getRequiredShipyardLevel(type));
            result.add(entry);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PlanetShip> getPlanetShips(Long planetId) {
        return planetShipRepository.findByPlanetId(planetId);
    }

    @Transactional
    public Map<String, Object> buildShips(Long planetId, ShipType shipType, int quantity, Long playerId) {
        // Validate planet ownership
        var planet = planetRepository.findById(planetId)
            .orElseThrow(() -> new IllegalArgumentException("Planet not found: " + planetId));
        if (!planet.getPlayerId().equals(playerId)) {
            throw new IllegalArgumentException("Planet does not belong to player");
        }

        // Validate shipyard level
        int shipyardLevel = buildingRepository
            .findByPlanetIdAndGridPosition(planetId, 9)
            .map(Building::getLevel)
            .orElse(0);
        if (shipyardLevel < gameBalancer.getRequiredShipyardLevel(shipType)) {
            throw new IllegalArgumentException("Shipyard level too low for " + shipType);
        }

        // Calculate total cost
        double metalCost = gameBalancer.getShipMetalCost(shipType) * quantity;
        double crystalCost = gameBalancer.getShipCrystalCost(shipType) * quantity;
        double gasCost = gameBalancer.getShipGasCost(shipType) * quantity;

        // Check resources (the client should have called recalculate first)
        if (planet.getMetal() < metalCost || planet.getCrystal() < crystalCost || planet.getGas() < gasCost) {
            throw new IllegalArgumentException("Insufficient resources");
        }

        // Deduct resources
        planet.setMetal(planet.getMetal() - metalCost);
        planet.setCrystal(planet.getCrystal() - crystalCost);
        planet.setGas(planet.getGas() - gasCost);
        planetRepository.save(planet);

        // Calculate build time
        int timeSeconds = gameBalancer.getShipBuildTimeSeconds(shipType, shipyardLevel, 0);

        // Create queue entry
        var queue = new ShipyardQueue();
        queue.setPlanetId(planetId);
        queue.setShipType(shipType);
        queue.setQuantity(quantity);
        queue.setBuiltQuantity(0);
        queue.setMetalCost(metalCost);
        queue.setCrystalCost(crystalCost);
        queue.setGasCost(gasCost);
        queue.setStartedAt(Instant.now());
        queue.setCompletesAt(Instant.now().plusSeconds(timeSeconds));
        shipyardQueueRepository.save(queue);

        Map<String, Object> result = new HashMap<>();
        result.put("queueId", queue.getId());
        result.put("shipType", shipType.name());
        result.put("quantity", quantity);
        result.put("completesAt", queue.getCompletesAt().toString());
        result.put("remainingSeconds", timeSeconds);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ShipyardQueue> getShipyardQueue(Long planetId) {
        return shipyardQueueRepository.findByPlanetIdAndCompletedFalseOrderByStartedAt(planetId);
    }

    @Transactional
    public void completeShipyardEntry(Long queueId) {
        var queue = shipyardQueueRepository.findById(queueId)
            .orElseThrow(() -> new IllegalArgumentException("Shipyard queue not found: " + queueId));
        queue.setCompleted(true);
        queue.setBuiltQuantity(queue.getQuantity());
        shipyardQueueRepository.save(queue);

        var planetShip = planetShipRepository
            .findByPlanetIdAndShipType(queue.getPlanetId(), queue.getShipType())
            .orElseGet(() -> planetShipRepository.save(new PlanetShip(queue.getPlanetId(), queue.getShipType())));
        planetShip.addQuantity(queue.getQuantity());
        planetShipRepository.save(planetShip);
    }

    @Transactional(readOnly = true)
    public List<ShipyardQueue> getCompletedShipyardEntries(Instant before) {
        return shipyardQueueRepository.findByCompletedFalseAndCompletesAtLessThanEqual(before);
    }
}
```

---

### Task 6: ShipyardController

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/web/ShipyardController.java`

- [ ] **Step 1: Create ShipyardController**

```java
package com.galaxyempire.game.web;

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

    @GetMapping("/ships")
    public ResponseEntity<?> getShipTypes() {
        return ResponseEntity.ok(ShipType.values());
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
}
```

---

### Task 7: GameLoopService - shipyard processor

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/GameLoopService.java`

- [ ] **Step 1: Add shipyard processing**

```java
// Add field
private final ShipyardService shipyardService;

// In the scheduled method, add:
processShipyardCompletions();

// Add method:
private void processShipyardCompletions() {
    var completed = shipyardService.getCompletedShipyardEntries(Instant.now());
    for (var queue : completed) {
        shipyardService.completeShipyardEntry(queue.getId());
        messagingTemplate.convertAndSend(
            "/topic/planet/" + queue.getPlanetId(),
            Map.of("type", "SHIP_BUILD_COMPLETE",
                   "shipType", queue.getShipType().name(),
                   "quantity", queue.getQuantity())
        );
    }
}
```

---

### Task 8: Frontend - Ship models and service methods

**Files:**
- Modify: `frontend/src/app/models.ts`
- Modify: `frontend/src/app/services/game.service.ts`

- [ ] **Step 1: Add interfaces to models.ts**

```typescript
export interface ShipType {
  shipType: string;
  metalCost: number;
  crystalCost: number;
  gasCost: number;
  timeSeconds: number;
  requiredShipyardLevel: number;
  available: boolean;
}

export interface PlanetShip {
  id: number;
  planetId: number;
  shipType: string;
  quantity: number;
}

export interface ShipyardQueue {
  id: number;
  planetId: number;
  shipType: string;
  quantity: number;
  completesAt: string;
  completed: boolean;
}
```

- [ ] **Step 2: Add shipyard API methods to game.service.ts**

```typescript
getPlanetShips(planetId: number): Observable<PlanetShip[]> {
  return this.http.get<PlanetShip[]>(`${this.apiUrl}/planets/${planetId}/ships`);
}

getAvailableShips(planetId: number): Observable<ShipTypeInfo[]> {
  return this.http.get<ShipTypeInfo[]>(`${this.apiUrl}/planets/${planetId}/shipyard`);
}

buildShips(planetId: number, shipType: string, quantity: number): Observable<any> {
  return this.http.post(`${this.apiUrl}/planets/${planetId}/ships/${shipType}/build`, { quantity });
}

getShipyardQueue(planetId: number): Observable<ShipyardQueue[]> {
  return this.http.get<ShipyardQueue[]>(`${this.apiUrl}/planets/${planetId}/shipyard-queue`);
}
```

---

### Task 9: Frontend - ShipyardComponent

**Files:**
- Create: `frontend/src/app/shipyard/shipyard.component.ts`
- Create: `frontend/src/app/shipyard/shipyard.component.html`
- Create: `frontend/src/app/shipyard/shipyard.component.css`

- [ ] **Step 1: Create ShipyardComponent**

```typescript
// shipyard.component.ts
import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GameService } from '../services/game.service';
import { PlanetShip, ShipyardQueue } from '../models';
import { Subscription } from 'rxjs';

interface ShipTypeInfo {
  shipType: string;
  metalCost: number;
  crystalCost: number;
  gasCost: number;
  timeSeconds: number;
  requiredShipyardLevel: number;
  available: boolean;
}

@Component({
  selector: 'app-shipyard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './shipyard.component.html',
  styleUrls: ['./shipyard.component.css']
})
export class ShipyardComponent implements OnInit, OnDestroy {
  planetId = 1;
  shipTypes: ShipTypeInfo[] = [];
  planetShips: PlanetShip[] = [];
  shipyardQueue: ShipyardQueue[] = [];
  buildQuantities: { [key: string]: number } = {};
  private wsSubscription?: Subscription;

  constructor(private gameService: GameService) {}

  ngOnInit() {
    this.loadData();
    this.connectWebSocket();
  }

  ngOnDestroy() {
    this.wsSubscription?.unsubscribe();
  }

  loadData() {
    this.gameService.getAvailableShips(this.planetId).subscribe(types => {
      this.shipTypes = types;
    });
    this.gameService.getPlanetShips(this.planetId).subscribe(ships => {
      this.planetShips = ships;
    });
    this.gameService.getShipyardQueue(this.planetId).subscribe(queue => {
      this.shipyardQueue = queue;
    });
  }

  connectWebSocket() {
    this.gameService.getMessages().subscribe((msg: any) => {
      if (msg.type === 'SHIP_BUILD_COMPLETE') {
        this.loadData();
      }
    });
  }

  getQuantity(type: string): number {
    return this.buildQuantities[type] || 1;
  }

  build(type: ShipTypeInfo) {
    if (!type.available) return;
    const qty = this.getQuantity(type.shipType);
    this.gameService.buildShips(this.planetId, type.shipType, qty).subscribe(() => {
      this.loadData();
    });
  }

  getExistingShips(type: string): number {
    const found = this.planetShips.find(s => s.shipType === type);
    return found ? found.quantity : 0;
  }

  getDisplayName(name: string): string {
    return name.split('_').map(w => w.charAt(0) + w.slice(1).toLowerCase()).join(' ');
  }

  formatTime(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h > 0) return `${h}h ${m}m`;
    if (m > 0) return `${m}m ${s}s`;
    return `${s}s`;
  }
}
```

- [ ] **Step 2: Create template**

```html
<!-- shipyard.component.html -->
<div class="shipyard-container">
  <h2>Shipyard</h2>

  <div class="existing-ships" *ngIf="planetShips.length > 0">
    <h3>Fleet at this planet</h3>
    <div class="ship-list">
      <div *ngFor="let ship of planetShips" class="ship-count">
        {{ getDisplayName(ship.shipType) }}: {{ ship.quantity }}
      </div>
    </div>
  </div>

  <div class="build-queue" *ngIf="shipyardQueue.length > 0">
    <h3>Building</h3>
    <div *ngFor="let q of shipyardQueue" class="queue-item">
      {{ getDisplayName(q.shipType) }} x{{ q.quantity }}
    </div>
  </div>

  <div class="ship-grid">
    <div *ngFor="let ship of shipTypes"
         class="ship-card"
         [class.available]="ship.available"
         [class.locked]="!ship.available">
      <div class="ship-name">{{ getDisplayName(ship.shipType) }}</div>
      <div class="ship-stats">
        <span>M: {{ ship.metalCost.toLocaleString() }}</span>
        <span>C: {{ ship.crystalCost.toLocaleString() }}</span>
        <span *ngIf="ship.gasCost > 0">G: {{ ship.gasCost.toLocaleString() }}</span>
      </div>
      <div class="ship-time">{{ formatTime(ship.timeSeconds) }}</div>
      <div class="ship-required" *ngIf="!ship.available">
        Requires Shipyard {{ ship.requiredShipyardLevel }}
      </div>
      <div class="build-controls" *ngIf="ship.available">
        <input type="number" min="1" max="9999"
               [value]="getQuantity(ship.shipType)"
               (change)="buildQuantities[ship.shipType] = $any($event.target).value">
        <button (click)="build(ship)">Build</button>
      </div>
      <div class="existing-count" *ngIf="getExistingShips(ship.shipType) > 0">
        Owned: {{ getExistingShips(ship.shipType) }}
      </div>
    </div>
  </div>
</div>
```

- [ ] **Step 3: Create styles**

```css
/* shipyard.component.css */
.shipyard-container { padding: 20px; color: #ccc; }
.ship-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 12px; margin-top: 16px; }
.ship-card { background: #1a1a2e; border: 1px solid #333; border-radius: 8px; padding: 16px; }
.ship-card.available { border-color: #4a9; }
.ship-card.locked { opacity: 0.5; }
.ship-name { font-size: 14px; font-weight: bold; color: #fff; }
.ship-stats { font-size: 11px; color: #aaa; margin-top: 8px; }
.ship-stats span { margin-right: 8px; }
.ship-time { font-size: 11px; color: #666; margin-top: 4px; }
.ship-required { font-size: 11px; color: #c66; margin-top: 8px; }
.build-controls { margin-top: 8px; display: flex; gap: 8px; }
.build-controls input { width: 70px; padding: 4px; background: #222; border: 1px solid #444; color: #fff; border-radius: 4px; }
.build-controls button { padding: 4px 12px; background: #4a9; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
.build-controls button:hover { background: #5ba; }
.existing-count { font-size: 11px; color: #888; margin-top: 4px; }
.existing-ships, .build-queue { background: #1a2a1a; border: 1px solid #4a9; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.ship-list { display: flex; gap: 16px; flex-wrap: wrap; }
.ship-count { font-size: 13px; color: #ccc; }
.queue-item { font-size: 13px; color: #fa0; padding: 4px 0; }
```

---

### Task 10: Frontend routes and navigation

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/app.component.html`

- [ ] **Step 1: Add shipyard route**

```typescript
// In app.routes.ts:
{ path: 'shipyard', component: ShipyardComponent, canActivate: [AuthGuard] },
```

- [ ] **Step 2: Add nav link**

```html
<a routerLink="/shipyard" routerLinkActive="active">Shipyard</a>
```
