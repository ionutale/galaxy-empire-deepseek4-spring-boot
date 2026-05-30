# Phase 7b-a: Core Dark Matter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add dark matter premium currency to game-service: player balance, earn/purchase endpoint, speed-up spending on construction/research/shipyard queues.

**Architecture:** New `player_resource` table in game-service (no cross-service calls). DarkMatterService owns balance operations. Each queue service gets a speed-up method. Frontend shows DM in header bar + speed-up buttons.

**Tech Stack:** Spring Boot 3.4, Java 21, PostgreSQL 16, Angular 19, Flyway V9

---

## File Structure

### Backend — Create:
- `backend/game-service/src/main/java/com/galaxyempire/game/domain/PlayerResource.java`
- `backend/game-service/src/main/java/com/galaxyempire/game/repository/PlayerResourceRepository.java`
- `backend/game-service/src/main/java/com/galaxyempire/game/service/DarkMatterService.java`
- `backend/game-service/src/main/resources/db/migration/V9__add_player_resource.sql`

### Backend — Modify:
- `backend/game-service/src/main/java/com/galaxyempire/game/service/BuildingService.java` (speedUpConstruction)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/ShipyardService.java` (speedUpShipyardEntry)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/ResearchService.java` (speedUpResearch)
- `backend/game-service/src/main/java/com/galaxyempire/game/web/PlanetController.java` (DM endpoints)
- `backend/game-service/src/main/java/com/galaxyempire/game/web/TechnologyController.java` (speed-up endpoint)

### Frontend — Modify:
- `frontend/src/app/core/models/models.ts` (DM types)
- `frontend/src/app/core/services/game.service.ts` (DM methods)
- `frontend/src/app/resource-bar/resource-bar.component.ts` (DM display)
- `frontend/src/app/overview/overview.component.ts` (speed-up button on queue)
- `frontend/src/app/shipyard/shipyard.component.ts` (speed-up button on queue)
- `frontend/src/app/research/research.component.ts` (speed-up button on queue)

---

### Task 1: Migration V9 + PlayerResource entity + repository

**Files:**
- Create: `backend/game-service/src/main/resources/db/migration/V9__add_player_resource.sql`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/PlayerResource.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/PlayerResourceRepository.java`

- [ ] **Create V9 migration**

```sql
CREATE TABLE player_resource (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL UNIQUE,
    dark_matter INT NOT NULL DEFAULT 0
);
```

- [ ] **Create PlayerResource entity**

```java
package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "player_resource")
public class PlayerResource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false, unique = true)
    private Long playerId;

    @Column(name = "dark_matter", nullable = false)
    private int darkMatter = 0;

    public PlayerResource() {}

    public PlayerResource(Long playerId) {
        this.playerId = playerId;
        this.darkMatter = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public int getDarkMatter() { return darkMatter; }
    public void setDarkMatter(int darkMatter) { this.darkMatter = darkMatter; }
}
```

- [ ] **Create PlayerResourceRepository**

```java
package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.PlayerResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerResourceRepository extends JpaRepository<PlayerResource, Long> {
    Optional<PlayerResource> findByPlayerId(Long playerId);
}
```

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Expected: BUILD SUCCESS

---

### Task 2: DarkMatterService

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/service/DarkMatterService.java`

- [ ] **Create DarkMatterService**

```java
package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.PlayerResource;
import com.galaxyempire.game.repository.PlayerResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DarkMatterService {

    private final PlayerResourceRepository playerResourceRepository;

    public DarkMatterService(PlayerResourceRepository playerResourceRepository) {
        this.playerResourceRepository = playerResourceRepository;
    }

    @Transactional(readOnly = true)
    public int getDarkMatter(Long playerId) {
        return playerResourceRepository.findByPlayerId(playerId)
            .map(PlayerResource::getDarkMatter)
            .orElse(0);
    }

    @Transactional
    public void addDarkMatter(Long playerId, int amount) {
        PlayerResource pr = playerResourceRepository.findByPlayerId(playerId)
            .orElseGet(() -> playerResourceRepository.save(new PlayerResource(playerId)));
        pr.setDarkMatter(pr.getDarkMatter() + amount);
        playerResourceRepository.save(pr);
    }

    @Transactional
    public boolean spendDarkMatter(Long playerId, int amount) {
        PlayerResource pr = playerResourceRepository.findByPlayerId(playerId).orElse(null);
        if (pr == null || pr.getDarkMatter() < amount) {
            return false;
        }
        pr.setDarkMatter(pr.getDarkMatter() - amount);
        playerResourceRepository.save(pr);
        return true;
    }

    public static int calculateSpeedUpCost(long remainingSeconds) {
        if (remainingSeconds <= 0) return 0;
        return Math.max(1, (int) Math.ceil(remainingSeconds / 1800.0));
    }
}
```

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Expected: BUILD SUCCESS

---

### Task 3: Speed-up in BuildingService

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/BuildingService.java`

- [ ] **Add speedUpConstruction method**

Read the current file. Add `speedUpConstruction(Long queueId, Long playerId)` method:

```java
    @Transactional
    public void speedUpConstruction(Long queueId, Long playerId) {
        ConstructionQueue queue = constructionQueueRepository.findById(queueId)
            .orElseThrow(() -> new IllegalArgumentException("Queue item not found"));
        long remainingSeconds = Duration.between(Instant.now(), queue.getCompletesAt()).getSeconds();
        int cost = DarkMatterService.calculateSpeedUpCost(remainingSeconds);
        if (cost > 0 && !darkMatterService.spendDarkMatter(playerId, cost)) {
            throw new IllegalArgumentException("Not enough dark matter");
        }
        queue.setCompletesAt(Instant.now());
        constructionQueueRepository.save(queue);
    }
```

Add imports:
```java
import java.time.Duration;
```

Inject `DarkMatterService darkMatterService` into the constructor.

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Expected: BUILD SUCCESS

---

### Task 4: Speed-up in ShipyardService

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/ShipyardService.java`

- [ ] **Add speedUpShipyardEntry method**

```java
    @Transactional
    public void speedUpShipyardEntry(Long queueId, Long playerId) {
        ShipyardQueue queue = shipyardQueueRepository.findById(queueId)
            .orElseThrow(() -> new IllegalArgumentException("Queue item not found"));
        long remainingSeconds = Duration.between(Instant.now(), queue.getCompletesAt()).getSeconds();
        int cost = DarkMatterService.calculateSpeedUpCost(remainingSeconds);
        if (cost > 0 && !darkMatterService.spendDarkMatter(playerId, cost)) {
            throw new IllegalArgumentException("Not enough dark matter");
        }
        queue.setCompletesAt(Instant.now());
        shipyardQueueRepository.save(queue);
    }
```

Add imports:
```java
import java.time.Duration;
```

Inject `DarkMatterService darkMatterService` into the constructor.

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Expected: BUILD SUCCESS

---

### Task 5: Speed-up in ResearchService

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/ResearchService.java`

- [ ] **Add speedUpResearch method**

```java
    @Transactional
    public void speedUpResearch(Long playerId, String technology) {
        ResearchQueue queue = researchQueueRepository
            .findByPlayerIdAndTechnologyAndCompletedFalse(playerId, Technology.valueOf(technology))
            .orElseThrow(() -> new IllegalArgumentException("No active research for " + technology));
        long remainingSeconds = Duration.between(Instant.now(), queue.getCompletesAt()).getSeconds();
        int cost = DarkMatterService.calculateSpeedUpCost(remainingSeconds);
        if (cost > 0 && !darkMatterService.spendDarkMatter(playerId, cost)) {
            throw new IllegalArgumentException("Not enough dark matter");
        }
        queue.setCompletesAt(Instant.now());
        researchQueueRepository.save(queue);
    }
```

Add imports:
```java
import java.time.Duration;
```

Inject `DarkMatterService darkMatterService` into the constructor.

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Expected: BUILD SUCCESS

---

### Task 6: Controller endpoints

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/web/PlanetController.java`
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/web/TechnologyController.java`

- [ ] **Add DM endpoints to PlanetController**

Inject `DarkMatterService darkMatterService`. Add:

```java
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
        buildingService.speedUpConstruction(queueId, playerId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/planets/{planetId}/shipyard/{queueId}/speed-up")
    public ResponseEntity<?> speedUpShipyard(@PathVariable Long planetId, @PathVariable Long queueId,
                                              @RequestHeader("X-Player-Id") Long playerId) {
        shipyardService.speedUpShipyardEntry(queueId, playerId);
        return ResponseEntity.ok(Map.of("success", true));
    }
```

May need to inject `BuildingService` and `ShipyardService` (they might already be injected). Add `DarkMatterService` injection.

- [ ] **Add speed-up endpoint to TechnologyController**

Inject `DarkMatterService darkMatterService` and `ResearchService researchService`. Add:

```java
    @PostMapping("/technologies/speed-up")
    public ResponseEntity<?> speedUpResearch(@RequestBody Map<String, String> body,
                                              @RequestHeader("X-Player-Id") Long playerId) {
        String technology = body.get("technology");
        researchService.speedUpResearch(playerId, technology);
        return ResponseEntity.ok(Map.of("success", true));
    }
```

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Expected: BUILD SUCCESS

---

### Task 7: Frontend models + service methods

**Files:**
- Modify: `frontend/src/app/core/models/models.ts`
- Modify: `frontend/src/app/core/services/game.service.ts`

- [ ] **Add DarkMatterResponse interface**

In `models.ts`, add:

```typescript
export interface DarkMatterResponse {
  darkMatter: number;
}
```

- [ ] **Add DM methods to GameService**

In `game.service.ts`, add:

```typescript
  getDarkMatter(playerId: number) {
    return this.http.get<DarkMatterResponse>(`${environment.apiUrl}/game/players/${playerId}/dark-matter`);
  }

  addDarkMatter(playerId: number, amount: number) {
    return this.http.post<DarkMatterResponse>(`${environment.apiUrl}/game/players/${playerId}/dark-matter/add`, { amount });
  }

  speedUpBuilding(planetId: number, queueId: number) {
    return this.http.post(`${environment.apiUrl}/game/planets/${planetId}/buildings/queue/${queueId}/speed-up`, {});
  }

  speedUpShipyard(planetId: number, queueId: number) {
    return this.http.post(`${environment.apiUrl}/game/planets/${planetId}/shipyard/${queueId}/speed-up`, {});
  }

  speedUpResearch(technology: string) {
    return this.http.post(`${environment.apiUrl}/game/technologies/speed-up`, { technology });
  }
```

Update import to include `DarkMatterResponse`.

- [ ] **Verify frontend builds**

Run: `cd /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend && npx ng build --configuration production 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

### Task 8: Frontend DM display in resource bar

**Files:**
- Modify: `frontend/src/app/resource-bar/resource-bar.component.ts`

- [ ] **Add dark matter display + polling**

Read the current resource-bar.component.ts. Add dark matter section:

In the `template`, after the energy span, add:
```html
      <span class="resource dark-matter">&#9670; Dark Matter: <b>{{ darkMatter }}</b></span>
```

In the class, add:
```typescript
  darkMatter = 0;
```

In ngOnInit, add a second subscription to poll dark matter:
```typescript
    // Dark matter polling
    this.sub.add(
      this.planetState.activePlanetId$.pipe(
        switchMap(planetId => {
          if (!planetId) return [];
          return interval(10000).pipe(
            switchMap(() => this.gameService.getDarkMatter(0)) // playerId from auth
          );
        })
      ).subscribe(data => {
        this.darkMatter = data.darkMatter;
      })
    );
```

Wait — the DM endpoint needs playerId, not planetId. Inject AuthService or use a different approach. The simplest: inject `AuthService` and get playerId from there:

```typescript
    // Dark matter polling (using auth playerId)
    this.sub.add(
      interval(10000).pipe(
        switchMap(() => {
          const pid = this.auth.getPlayerId();
          return pid ? this.gameService.getDarkMatter(pid) : [];
        })
      ).subscribe(data => {
        this.darkMatter = data.darkMatter;
      })
    );
```

Inject `AuthService` into the constructor:
```typescript
import { AuthService } from '../core/services/auth.service';
// ...
constructor(
    private gameService: GameService,
    private planetState: PlanetStateService,
    private auth: AuthService
  ) {}
```

Add a `.dark-matter` style:
```css
    .dark-matter { color: #a855f7; }
```

- [ ] **Verify frontend builds**

Run: `cd /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend && npx ng build --configuration production 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

### Task 9: Frontend speed-up buttons

**Files:**
- Modify: `frontend/src/app/overview/overview.component.ts`
- Modify: `frontend/src/app/shipyard/shipyard.component.ts`
- Modify: `frontend/src/app/research/research.component.ts`

- [ ] **Add speed-up button to OverviewComponent queue items**

In the template, after the timer span in each queue item, add a speed-up button:
```html
          <button class="speed-up" (click)="speedUpBuilding(q.id, $event)">Speed Up</button>
```

Add the method:
```typescript
  speedUpBuilding(queueId: number, event: Event) {
    event.stopPropagation();
    const pid = this.planet()?.id;
    if (!pid) return;
    this.game.speedUpBuilding(pid, queueId).subscribe(() => {
      this.loadPlanet();
    });
  }
```

Add style:
```css
    .speed-up { background: #7c3aed; color: #fff; border: none; padding: 2px 8px; border-radius: 4px; cursor: pointer; font-size: 11px; margin-left: 8px; }
    .speed-up:hover { background: #6d28d9; }
```

- [ ] **Add speed-up button to ShipyardComponent**

Read the current shipyard.component.ts. Find where queue items are rendered. Add a "Speed Up" button similar to above, calling `this.game.speedUpShipyard(planetId, queue.id)`.

- [ ] **Add speed-up button to ResearchComponent**

Read the current research.component.ts. Find where active research is displayed. Add a "Speed Up" button calling `this.game.speedUpResearch(technologyName)`.

- [ ] **Verify frontend builds**

Run: `cd /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend && npx ng build --configuration production 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

### Task 10: Final verification

- [ ] **Full backend build + tests**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am clean verify -DskipTests=false 2>&1 | tail -15`
Expected: BUILD SUCCESS

- [ ] **Full frontend build**

Run: `cd /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend && npx ng build --configuration production 2>&1 | tail -10`
Expected: BUILD SUCCESS (only pre-existing CommonJS warning)

- [ ] **Report results**
