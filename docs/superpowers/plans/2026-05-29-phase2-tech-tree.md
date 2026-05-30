# Phase 2: Technology Tree Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add technology research system with Research Lab, 13 technologies, research queue, and tech tree UI.

**Architecture:** Flyway migration → entities → GameBalancer formulas → ResearchService → TechnologyController + research queue processing in GameLoop → frontend research view. Follows the existing construction_queue pattern.

**Tech Stack:** Spring Boot 3.4, Java 21, PostgreSQL 16, Flyway, Angular 19

---

### Task 1: Database migration V4 (technology + research_queue)

**Files:**
- Create: `backend/game-service/src/main/resources/db/migration/V4__create_technology_and_research.sql`

- [ ] **Step 1: Create the migration**

```sql
CREATE TABLE player_technology (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    technology VARCHAR(32) NOT NULL,
    level INT NOT NULL DEFAULT 0,
    UNIQUE (player_id, technology)
);

CREATE TABLE research_queue (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    technology VARCHAR(32) NOT NULL,
    target_level INT NOT NULL,
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

### Task 2: Technology enum

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/Technology.java`

- [ ] **Step 1: Create the enum**

```java
package com.galaxyempire.game.domain;

public enum Technology {
    ENERGY_TECH,
    LASER_TECH,
    ION_TECH,
    PLASMA_TECH,
    COMBUSTION_DRIVE,
    IMPULSE_DRIVE,
    HYPERSPACE_DRIVE,
    WEAPON_TECH,
    SHIELDING_TECH,
    ARMOR_TECH,
    COMPUTER_TECH,
    ESPIONAGE_TECH,
    GRAVITON_TECH
}
```

---

### Task 3: JPA entities

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/PlayerTechnology.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/ResearchQueue.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/PlayerTechnologyRepository.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/ResearchQueueRepository.java`

- [ ] **Step 1: Create PlayerTechnology entity**

```java
package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "player_technology", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"player_id", "technology"})
})
public class PlayerTechnology {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Technology technology;

    @Column(nullable = false)
    private int level = 0;

    public PlayerTechnology() {}

    public PlayerTechnology(Long playerId, Technology technology) {
        this.playerId = playerId;
        this.technology = technology;
        this.level = 0;
    }

    public Long getId() { return id; }
    public Long getPlayerId() { return playerId; }
    public Technology getTechnology() { return technology; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
}
```

- [ ] **Step 2: Create ResearchQueue entity**

```java
package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "research_queue")
public class ResearchQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Technology technology;

    @Column(name = "target_level", nullable = false)
    private int targetLevel;

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

    public ResearchQueue() {}

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public Technology getTechnology() { return technology; }
    public void setTechnology(Technology technology) { this.technology = technology; }
    public int getTargetLevel() { return targetLevel; }
    public void setTargetLevel(int targetLevel) { this.targetLevel = targetLevel; }
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
// PlayerTechnologyRepository.java
package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.PlayerTechnology;
import com.galaxyempire.game.domain.Technology;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlayerTechnologyRepository extends JpaRepository<PlayerTechnology, Long> {
    List<PlayerTechnology> findByPlayerId(Long playerId);
    Optional<PlayerTechnology> findByPlayerIdAndTechnology(Long playerId, Technology technology);
}
```

```java
// ResearchQueueRepository.java
package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.ResearchQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ResearchQueueRepository extends JpaRepository<ResearchQueue, Long> {
    List<ResearchQueue> findByPlayerIdAndCompletedFalse(Long playerId);
    Optional<ResearchQueue> findByPlayerIdAndCompletedFalseAndTechnology(Long playerId, Technology technology);
    List<ResearchQueue> findByCompletedFalseAndCompletesAtLessThanEqual(java.time.Instant now);
    boolean existsByPlayerIdAndCompletedFalse(Long playerId);
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd backend && mvn -pl game-service -am compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 4: GameBalancer - technology formulas

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/GameBalancer.java`

- [ ] **Step 1: Add technology cost and time methods**

Add after the existing methods (before the closing brace):

```java
public double getTechnologyMetalCost(Technology tech, int level) {
    long base = switch (tech) {
        case ENERGY_TECH -> 200;
        case LASER_TECH -> 100;
        case ION_TECH -> 250;
        case PLASMA_TECH -> 500;
        case COMBUSTION_DRIVE -> 200;
        case IMPULSE_DRIVE -> 1000;
        case HYPERSPACE_DRIVE -> 2000;
        case WEAPON_TECH -> 400;
        case SHIELDING_TECH -> 200;
        case ARMOR_TECH -> 200;
        case COMPUTER_TECH -> 100;
        case ESPIONAGE_TECH -> 200;
        case GRAVITON_TECH -> 5000;
    };
    return Math.floor(base * Math.pow(2, level)) * speed;
}

public double getTechnologyCrystalCost(Technology tech, int level) {
    long base = switch (tech) {
        case ENERGY_TECH -> 100;
        case LASER_TECH -> 50;
        case ION_TECH -> 150;
        case PLASMA_TECH -> 300;
        case COMBUSTION_DRIVE -> 100;
        case IMPULSE_DRIVE -> 500;
        case HYPERSPACE_DRIVE -> 1000;
        case WEAPON_TECH -> 200;
        case SHIELDING_TECH -> 400;
        case ARMOR_TECH -> 100;
        case COMPUTER_TECH -> 200;
        case ESPIONAGE_TECH -> 400;
        case GRAVITON_TECH -> 5000;
    };
    return Math.floor(base * Math.pow(2, level)) * speed;
}

public double getTechnologyGasCost(Technology tech, int level) {
    long base = switch (tech) {
        case PLASMA_TECH, IMPULSE_DRIVE, HYPERSPACE_DRIVE, ESPIONAGE_TECH -> 100;
        case GRAVITON_TECH -> 1000;
        default -> 0;
    };
    if (tech == HYPERSPACE_DRIVE) base = 500;
    if (base == 0) return 0;
    return Math.floor(base * Math.pow(2, level)) * speed;
}

public int getResearchTimeSeconds(Technology tech, int level, double researchLabLevel) {
    long base = switch (tech) {
        case ENERGY_TECH -> 600;
        case LASER_TECH -> 400;
        case ION_TECH -> 800;
        case PLASMA_TECH -> 2000;
        case COMBUSTION_DRIVE -> 600;
        case IMPULSE_DRIVE -> 1800;
        case HYPERSPACE_DRIVE -> 3600;
        case WEAPON_TECH -> 1200;
        case SHIELDING_TECH -> 1200;
        case ARMOR_TECH -> 600;
        case COMPUTER_TECH -> 400;
        case ESPIONAGE_TECH -> 1200;
        case GRAVITON_TECH -> 14400;
    };
    double time = base * Math.pow(2, level) / (1 + researchLabLevel);
    return (int) Math.ceil(time / speed);
}
```

- [ ] **Step 2: Add prerequisite check method**

```java
public boolean meetsPrerequisites(Technology tech, Map<Technology, Integer> playerTechLevels) {
    return switch (tech) {
        case PLASMA_TECH ->
            playerTechLevels.getOrDefault(Technology.ENERGY_TECH, 0) >= 5
            && playerTechLevels.getOrDefault(Technology.LASER_TECH, 0) >= 5;
        case COMBUSTION_DRIVE ->
            playerTechLevels.getOrDefault(Technology.ENERGY_TECH, 0) >= 1;
        case IMPULSE_DRIVE ->
            playerTechLevels.getOrDefault(Technology.COMBUSTION_DRIVE, 0) >= 5
            && playerTechLevels.getOrDefault(Technology.ENERGY_TECH, 0) >= 2;
        case HYPERSPACE_DRIVE ->
            playerTechLevels.getOrDefault(Technology.IMPULSE_DRIVE, 0) >= 5
            && playerTechLevels.getOrDefault(Technology.ENERGY_TECH, 0) >= 3;
        case WEAPON_TECH ->
            playerTechLevels.getOrDefault(Technology.LASER_TECH, 0) >= 3;
        case SHIELDING_TECH ->
            playerTechLevels.getOrDefault(Technology.ENERGY_TECH, 0) >= 3;
        case ESPIONAGE_TECH ->
            playerTechLevels.getOrDefault(Technology.COMPUTER_TECH, 0) >= 3;
        case GRAVITON_TECH ->
            playerTechLevels.getOrDefault(Technology.ENERGY_TECH, 0) >= 10
            && playerTechLevels.getOrDefault(Technology.PLASMA_TECH, 0) >= 5;
        default -> true;
    };
}
```

- [ ] **Step 3: Add technology effect method**

```java
public double getTechnologyEffect(Technology tech, int level) {
    return 1.0 + 0.05 * level;
}
```

---

### Task 5: ResearchService

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/service/ResearchService.java`

- [ ] **Step 1: Create ResearchService**

```java
package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ResearchService {

    private final PlayerTechnologyRepository playerTechnologyRepository;
    private final ResearchQueueRepository researchQueueRepository;
    private final BuildingRepository buildingRepository;
    private final GameBalancer gameBalancer;

    public ResearchService(PlayerTechnologyRepository playerTechnologyRepository,
                           ResearchQueueRepository researchQueueRepository,
                           BuildingRepository buildingRepository,
                           GameBalancer gameBalancer) {
        this.playerTechnologyRepository = playerTechnologyRepository;
        this.researchQueueRepository = researchQueueRepository;
        this.buildingRepository = buildingRepository;
        this.gameBalancer = gameBalancer;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTechnologies(Long playerId) {
        var playerTechs = playerTechnologyRepository.findByPlayerId(playerId);
        var techLevelMap = playerTechs.stream()
            .collect(Collectors.toMap(PlayerTechnology::getTechnology, PlayerTechnology::getLevel));
        var activeQueue = researchQueueRepository.findByPlayerIdAndCompletedFalse(playerId);
        var activeTech = activeQueue.isEmpty() ? null : activeQueue.get(0).getTechnology();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Technology tech : Technology.values()) {
            int currentLevel = techLevelMap.getOrDefault(tech, 0);
            Map<String, Object> entry = new HashMap<>();
            entry.put("technology", tech.name());
            entry.put("level", currentLevel);
            entry.put("metalCost", gameBalancer.getTechnologyMetalCost(tech, currentLevel));
            entry.put("crystalCost", gameBalancer.getTechnologyCrystalCost(tech, currentLevel));
            entry.put("gasCost", gameBalancer.getTechnologyGasCost(tech, currentLevel));
            entry.put("timeSeconds", gameBalancer.getResearchTimeSeconds(tech, currentLevel,
                getHighestResearchLabLevel(playerId)));
            entry.put("prerequisitesMet", gameBalancer.meetsPrerequisites(tech, techLevelMap));
            entry.put("isResearching", tech == activeTech);
            result.add(entry);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTechnologyDetails(Long playerId, Technology tech) {
        var playerTech = playerTechnologyRepository.findByPlayerIdAndTechnology(playerId, tech);
        int currentLevel = playerTech.map(PlayerTechnology::getLevel).orElse(0);
        var techLevelMap = playerTechnologyRepository.findByPlayerId(playerId).stream()
            .collect(Collectors.toMap(PlayerTechnology::getTechnology, PlayerTechnology::getLevel));

        Map<String, Object> result = new HashMap<>();
        result.put("technology", tech.name());
        result.put("level", currentLevel);
        result.put("metalCost", gameBalancer.getTechnologyMetalCost(tech, currentLevel));
        result.put("crystalCost", gameBalancer.getTechnologyCrystalCost(tech, currentLevel));
        result.put("gasCost", gameBalancer.getTechnologyGasCost(tech, currentLevel));
        result.put("timeSeconds", gameBalancer.getResearchTimeSeconds(tech, currentLevel,
            getHighestResearchLabLevel(playerId)));
        result.put("prerequisitesMet", gameBalancer.meetsPrerequisites(tech, techLevelMap));
        return result;
    }

    @Transactional
    public Map<String, Object> startResearch(Long playerId, Technology tech) {
        if (researchQueueRepository.existsByPlayerIdAndCompletedFalse(playerId)) {
            throw new IllegalArgumentException("Already researching a technology");
        }
        var playerTech = playerTechnologyRepository
            .findByPlayerIdAndTechnology(playerId, tech)
            .orElseGet(() -> playerTechnologyRepository.save(new PlayerTechnology(playerId, tech)));

        int currentLevel = playerTech.getLevel();
        var techLevelMap = playerTechnologyRepository.findByPlayerId(playerId).stream()
            .collect(Collectors.toMap(PlayerTechnology::getTechnology, PlayerTechnology::getLevel));

        if (!gameBalancer.meetsPrerequisites(tech, techLevelMap)) {
            throw new IllegalArgumentException("Prerequisites not met for " + tech);
        }

        double metalCost = gameBalancer.getTechnologyMetalCost(tech, currentLevel);
        double crystalCost = gameBalancer.getTechnologyCrystalCost(tech, currentLevel);
        double gasCost = gameBalancer.getTechnologyGasCost(tech, currentLevel);
        int timeSeconds = gameBalancer.getResearchTimeSeconds(tech, currentLevel,
            getHighestResearchLabLevel(playerId));

        // Resources are deducted from the player's planets (sum all resources across planets)
        // In the current architecture resources are per-planet, so we deduct from a planet
        // For now: deduct from first planet. This will need refinement with resource pooling.
        // Actually, we need PlanetRepository to get player's planets.
        // We'll pass this through the controller which handles the resource deduction.

        var queue = new ResearchQueue();
        queue.setPlayerId(playerId);
        queue.setTechnology(tech);
        queue.setTargetLevel(currentLevel + 1);
        queue.setMetalCost(metalCost);
        queue.setCrystalCost(crystalCost);
        queue.setGasCost(gasCost);
        queue.setStartedAt(Instant.now());
        queue.setCompletesAt(Instant.now().plusSeconds(timeSeconds));
        researchQueueRepository.save(queue);

        Map<String, Object> result = new HashMap<>();
        result.put("queueId", queue.getId());
        result.put("technology", tech.name());
        result.put("targetLevel", currentLevel + 1);
        result.put("completesAt", queue.getCompletesAt().toString());
        result.put("remainingSeconds", timeSeconds);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ResearchQueue> getActiveResearch(Long playerId) {
        return researchQueueRepository.findByPlayerIdAndCompletedFalse(playerId);
    }

    @Transactional
    public void completeResearch(Long queueId) {
        var queue = researchQueueRepository.findById(queueId)
            .orElseThrow(() -> new IllegalArgumentException("Research queue not found: " + queueId));
        queue.setCompleted(true);
        researchQueueRepository.save(queue);

        var playerTech = playerTechnologyRepository
            .findByPlayerIdAndTechnology(queue.getPlayerId(), queue.getTechnology())
            .orElseThrow();
        playerTech.setLevel(queue.getTargetLevel());
        playerTechnologyRepository.save(playerTech);
    }

    @Transactional(readOnly = true)
    public List<ResearchQueue> getCompletedResearches(Instant before) {
        return researchQueueRepository.findByCompletedFalseAndCompletesAtLessThanEqual(before);
    }

    private double getHighestResearchLabLevel(Long playerId) {
        var researchLabs = buildingRepository.findByPlayerId(playerId)
            .stream()
            .filter(b -> b.getBuildingType() == BuildingType.RESEARCH_LAB)
            .mapToInt(Building::getLevel)
            .max();
        return researchLabs.orElse(0);
    }
}
```

- [ ] **Step 2: Add `findByPlayerId` to BuildingRepository**

Check `BuildingRepository.java` — if it doesn't have `findByPlayerId`, add:
```java
// In BuildingRepository.java
@Query("SELECT b FROM Building b WHERE b.planetId IN (SELECT p.id FROM Planet p WHERE p.playerId = :playerId)")
List<Building> findByPlayerId(@Param("playerId") Long playerId);
```

**Note:** First check if this method or a similar one already exists. The Planet entity has `playerId`, and Building has `planetId`, so we need a cross-entity query. If the query is complex, simplify by getting the player's planets first, then their buildings.

---

### Task 6: TechnologyController

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/web/TechnologyController.java`
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/PlanetService.java` (for resource deduction)

- [ ] **Step 1: Create TechnologyController**

```java
package com.galaxyempire.game.web;

import com.galaxyempire.game.domain.Technology;
import com.galaxyempire.game.service.ResearchService;
import com.galaxyempire.game.service.ResourceService;
import com.galaxyempire.game.repository.PlanetRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class TechnologyController {

    private final ResearchService researchService;
    private final ResourceService resourceService;
    private final PlanetRepository planetRepository;

    public TechnologyController(ResearchService researchService,
                                ResourceService resourceService,
                                PlanetRepository planetRepository) {
        this.researchService = researchService;
        this.resourceService = resourceService;
        this.planetRepository = planetRepository;
    }

    @GetMapping("/technologies")
    public ResponseEntity<?> getTechnologies(@RequestHeader("X-Player-Id") Long playerId) {
        return ResponseEntity.ok(researchService.getTechnologies(playerId));
    }

    @GetMapping("/technologies/{name}")
    public ResponseEntity<?> getTechnology(@PathVariable String name,
                                           @RequestHeader("X-Player-Id") Long playerId) {
        try {
            Technology tech = Technology.valueOf(name.toUpperCase());
            return ResponseEntity.ok(researchService.getTechnologyDetails(playerId, tech));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown technology: " + name));
        }
    }

    @PostMapping("/technologies/{name}/research")
    public ResponseEntity<?> startResearch(@PathVariable String name,
                                           @RequestHeader("X-Player-Id") Long playerId) {
        try {
            Technology tech = Technology.valueOf(name.toUpperCase());
            var result = researchService.startResearch(playerId, tech);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/planets/{planetId}/research-queue")
    public ResponseEntity<?> getResearchQueue(@RequestHeader("X-Player-Id") Long playerId) {
        return ResponseEntity.ok(researchService.getActiveResearch(playerId));
    }
}
```

- [ ] **Step 2: Add `findByPlayerId` to PlanetRepository if missing**

---

### Task 7: GameLoopService - research processor

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/GameLoopService.java`

- [ ] **Step 1: Add research processing to the scheduled tick**

```java
// Add these fields
private final ResearchService researchService;
private final SimpMessagingTemplate messagingTemplate;

// In the scheduled method, add after construction processing:
processResearchCompletions();

// Add this method:
private void processResearchCompletions() {
    var completed = researchService.getCompletedResearches(Instant.now());
    for (var queue : completed) {
        researchService.completeResearch(queue.getId());
        messagingTemplate.convertAndSend(
            "/topic/research/" + queue.getPlayerId(),
            Map.of("type", "RESEARCH_COMPLETE",
                   "technology", queue.getTechnology().name(),
                   "level", queue.getTargetLevel())
        );
    }
}
```

Make sure `SimpMessagingTemplate` is already injected or add it as a constructor parameter.

---

### Task 8: Frontend - Technology models and service methods

**Files:**
- Modify: `frontend/src/app/models.ts`
- Modify: `frontend/src/app/services/game.service.ts`

- [ ] **Step 1: Add Technology interface to models.ts**

```typescript
export interface Technology {
  technology: string;
  level: number;
  metalCost: number;
  crystalCost: number;
  gasCost: number;
  timeSeconds: number;
  prerequisitesMet: boolean;
  isResearching: boolean;
}

export interface ResearchQueue {
  id: number;
  playerId: number;
  technology: string;
  targetLevel: number;
  completesAt: string;
  completed: boolean;
}
```

- [ ] **Step 2: Add tech API methods to game.service.ts**

```typescript
getTechnologies(): Observable<Technology[]> {
  return this.http.get<Technology[]>(`${this.apiUrl}/technologies`);
}

getTechnology(name: string): Observable<Technology> {
  return this.http.get<Technology>(`${this.apiUrl}/technologies/${name}`);
}

startResearch(name: string): Observable<any> {
  return this.http.post(`${this.apiUrl}/technologies/${name}/research`, {});
}

getResearchQueue(): Observable<ResearchQueue[]> {
  return this.http.get<ResearchQueue[]>(`${this.apiUrl}/planets/${this.planetId}/research-queue`);
}
```

---

### Task 9: Frontend - ResearchComponent

**Files:**
- Create: `frontend/src/app/research/research.component.ts`
- Create: `frontend/src/app/research/research.component.html`
- Create: `frontend/src/app/research/research.component.css`

- [ ] **Step 1: Create ResearchComponent**

```typescript
// research.component.ts
import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GameService } from '../services/game.service';
import { Technology, ResearchQueue } from '../models';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-research',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './research.component.html',
  styleUrls: ['./research.component.css']
})
export class ResearchComponent implements OnInit, OnDestroy {
  technologies: Technology[] = [];
  activeResearch: ResearchQueue | null = null;
  private wsSubscription?: Subscription;

  constructor(private gameService: GameService) {}

  ngOnInit() {
    this.loadTechnologies();
    this.connectWebSocket();
  }

  ngOnDestroy() {
    this.wsSubscription?.unsubscribe();
  }

  loadTechnologies() {
    this.gameService.getTechnologies().subscribe(techs => {
      this.technologies = techs;
      this.activeResearch = null;
    });
    this.gameService.getResearchQueue().subscribe(queue => {
      if (queue.length > 0) {
        this.activeResearch = queue[0];
      }
    });
  }

  connectWebSocket() {
    this.gameService.getMessages().subscribe((msg: any) => {
      if (msg.type === 'RESEARCH_COMPLETE') {
        this.loadTechnologies();
      }
    });
  }

  research(tech: Technology) {
    if (tech.prerequisitesMet && !tech.isResearching) {
      this.gameService.startResearch(tech.technology).subscribe(() => {
        this.loadTechnologies();
      });
    }
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

  getProgressPercent(): number {
    if (!this.activeResearch) return 0;
    const total = this.activeResearch.targetLevel;
    return 50; // simplified
  }
}
```

- [ ] **Step 2: Create template**

```html
<!-- research.component.html -->
<div class="research-container">
  <h2>Research Lab</h2>

  <div class="active-research" *ngIf="activeResearch">
    <h3>Active Research</h3>
    <p>{{ getDisplayName(activeResearch.technology) }} → Level {{ activeResearch.targetLevel }}</p>
    <div class="progress-bar">
      <div class="progress-fill" [style.width.%]="getProgressPercent()"></div>
    </div>
  </div>

  <div class="tech-grid">
    <div *ngFor="let tech of technologies"
         class="tech-card"
         [class.can-research]="tech.prerequisitesMet && !tech.isResearching"
         [class.no-prereqs]="!tech.prerequisitesMet"
         [class.researching]="tech.isResearching"
         (click)="research(tech)">
      <div class="tech-name">{{ getDisplayName(tech.technology) }}</div>
      <div class="tech-level">Level {{ tech.level }}</div>
      <div class="tech-cost" *ngIf="tech.level < 50">
        <span *ngIf="tech.metalCost > 0">M: {{ tech.metalCost.toLocaleString() }}</span>
        <span *ngIf="tech.crystalCost > 0">C: {{ tech.crystalCost.toLocaleString() }}</span>
        <span *ngIf="tech.gasCost > 0">G: {{ tech.gasCost.toLocaleString() }}</span>
      </div>
      <div class="tech-time">{{ formatTime(tech.timeSeconds) }}</div>
      <div class="tech-status" *ngIf="tech.isResearching">🔬 Researching...</div>
      <div class="tech-status" *ngIf="!tech.prerequisitesMet && !tech.isResearching">⚠ Prerequisites not met</div>
      <div class="tech-status" *ngIf="tech.prerequisitesMet && !tech.isResearching && tech.level < 50">▶ Research</div>
    </div>
  </div>
</div>
```

- [ ] **Step 3: Create styles**

```css
/* research.component.css */
.research-container { padding: 20px; color: #ccc; }
.tech-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; margin-top: 16px; }
.tech-card { background: #1a1a2e; border: 1px solid #333; border-radius: 8px; padding: 16px; cursor: pointer; transition: all 0.2s; }
.tech-card.can-research { border-color: #4a9; }
.tech-card.can-research:hover { border-color: #6cf; background: #1e2a3e; }
.tech-card.no-prereqs { opacity: 0.5; cursor: not-allowed; }
.tech-card.researching { border-color: #fa0; }
.tech-name { font-size: 14px; font-weight: bold; color: #fff; }
.tech-level { font-size: 12px; color: #888; margin-top: 4px; }
.tech-cost { font-size: 11px; color: #aaa; margin-top: 8px; }
.tech-cost span { margin-right: 8px; }
.tech-time { font-size: 11px; color: #666; margin-top: 4px; }
.tech-status { font-size: 11px; margin-top: 8px; }
.active-research { background: #1a2a1a; border: 1px solid #4a9; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.progress-bar { height: 8px; background: #333; border-radius: 4px; margin-top: 8px; overflow: hidden; }
.progress-fill { height: 100%; background: #4a9; transition: width 1s; }
```

---

### Task 10: Frontend routes and navigation

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/app.component.html` (or the shell component with nav)

- [ ] **Step 1: Add research route**

```typescript
// In app.routes.ts routes array:
{ path: 'research', component: ResearchComponent, canActivate: [AuthGuard] },
```

Make sure to import `ResearchComponent`.

- [ ] **Step 2: Add nav link** — add `/research` to the navigation bar in whichever component renders the nav (likely `app.component.html`).

```html
<a routerLink="/research" routerLinkActive="active">Research</a>
```
