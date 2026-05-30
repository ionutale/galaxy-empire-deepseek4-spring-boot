# Phase 6: Galaxy Map Implementation Plan

> **For agentic workers:** Subagent-driven development. Steps use checkbox syntax.

**Goal:** Add 3-level galaxy map navigation (Galaxy Grid → System List → Slot Detail)

**Architecture:** Backend: new GalaxyController + GalaxyService with 2 endpoints querying existing tables. Frontend: new GalaxyComponent with 3 view states, inline template/styles.

---

### Task 1: Backend — GalaxyRepository queries + GalaxyService

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/service/GalaxyService.java`
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/repository/PlanetRepository.java`

- [ ] **Step 1: Add system queries to PlanetRepository**

Read the current file at `backend/game-service/src/main/java/com/galaxyempire/game/repository/PlanetRepository.java`. Add:

```java
    @Query("SELECT p.systemId FROM Planet p WHERE p.galaxy = :galaxy")
    List<Integer> findSystemIdsByGalaxy(@Param("galaxy") int galaxy);

    List<Planet> findByGalaxyAndSystemId(int galaxy, int systemId);
```

- [ ] **Step 2: Create GalaxyService**

Create `backend/game-service/src/main/java/com/galaxyempire/game/service/GalaxyService.java`:

```java
package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GalaxyService {

    private final PlanetRepository planetRepository;
    private final PlanetShipRepository planetShipRepository;
    private final PlanetDefenseRepository planetDefenseRepository;
    private final DebrisFieldRepository debrisFieldRepository;

    public GalaxyService(PlanetRepository planetRepository,
                         PlanetShipRepository planetShipRepository,
                         PlanetDefenseRepository planetDefenseRepository,
                         DebrisFieldRepository debrisFieldRepository) {
        this.planetRepository = planetRepository;
        this.planetShipRepository = planetShipRepository;
        this.planetDefenseRepository = planetDefenseRepository;
        this.debrisFieldRepository = debrisFieldRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSystemList(int galaxy, Long playerId) {
        Set<Integer> occupied = new HashSet<>(planetRepository.findSystemIdsByGalaxy(galaxy));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int systemId = 1; systemId <= 500; systemId++) {
            boolean occupiedBySomeone = occupied.contains(systemId);
            int planetCount = occupiedBySomeone ? countPlanetsInSystem(galaxy, systemId) : 0;
            boolean hasOwnPlanet = occupiedBySomeone && hasPlayerPlanetInSystem(galaxy, systemId, playerId);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("systemId", systemId);
            entry.put("planetCount", planetCount);
            entry.put("hasOwnPlanet", hasOwnPlanet);
            result.add(entry);
        }
        return result;
    }

    private int countPlanetsInSystem(int galaxy, int systemId) {
        return planetRepository.findByGalaxyAndSystemId(galaxy, systemId).size();
    }

    private boolean hasPlayerPlanetInSystem(int galaxy, int systemId, Long playerId) {
        return planetRepository.findByGalaxyAndSystemId(galaxy, systemId).stream()
            .anyMatch(p -> p.getPlayerId().equals(playerId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSystemDetail(int galaxy, int systemId, Long playerId) {
        List<Planet> planets = planetRepository.findByGalaxyAndSystemId(galaxy, systemId);
        Map<Integer, Planet> planetBySlot = new HashMap<>();
        for (Planet p : planets) {
            planetBySlot.put(p.getSlot(), p);
        }

        List<Map<String, Object>> slots = new ArrayList<>();
        for (int slot = 1; slot <= 15; slot++) {
            Planet p = planetBySlot.get(slot);
            if (p == null) {
                slots.add(Map.of("slot", slot, "occupied", false));
            } else {
                int fleetCount = planetShipRepository.findByPlanetId(p.getId()).stream()
                    .mapToInt(PlanetShip::getQuantity).sum();
                int defenseCount = planetDefenseRepository.findByPlanetId(p.getId()).stream()
                    .mapToInt(PlanetDefense::getQuantity).sum();
                Optional<DebrisField> df = debrisFieldRepository.findByPlanetId(p.getId());
                double debrisMetal = df.map(DebrisField::getMetal).orElse(0.0);
                double debrisCrystal = df.map(DebrisField::getCrystal).orElse(0.0);

                Map<String, Object> slotEntry = new LinkedHashMap<>();
                slotEntry.put("slot", slot);
                slotEntry.put("occupied", true);
                slotEntry.put("planetId", p.getId());
                slotEntry.put("planetName", p.getName());
                slotEntry.put("playerId", p.getPlayerId());
                slotEntry.put("playerName", "Player " + p.getPlayerId());
                slotEntry.put("isOwn", p.getPlayerId().equals(playerId));
                slotEntry.put("fleetCount", fleetCount);
                slotEntry.put("defenseCount", defenseCount);
                slotEntry.put("debrisMetal", debrisMetal);
                slotEntry.put("debrisCrystal", debrisCrystal);
                slots.add(slotEntry);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("galaxy", galaxy);
        result.put("systemId", systemId);
        result.put("slots", slots);
        return result;
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`

---

### Task 2: Backend — GalaxyController

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/web/GalaxyController.java`

- [ ] **Step 1: Create GalaxyController**

```java
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
```

- [ ] **Step 2: Verify compilation**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`

---

### Task 3: Frontend — models + service methods + route + nav

**Files:**
- Modify: `frontend/src/app/core/models/models.ts`
- Modify: `frontend/src/app/core/services/game.service.ts`
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/app.component.ts`

- [ ] **Step 1: Add interfaces to models.ts**

Read the current file. Add at the end:

```typescript
export interface SystemInfo {
  systemId: number;
  planetCount: number;
  hasOwnPlanet: boolean;
}

export interface SlotInfo {
  slot: number;
  occupied: boolean;
  planetId?: number;
  planetName?: string;
  playerName?: string;
  playerId?: number;
  isOwn?: boolean;
  fleetCount?: number;
  defenseCount?: number;
  debrisMetal?: number;
  debrisCrystal?: number;
}

export interface SystemDetail {
  galaxy: number;
  systemId: number;
  slots: SlotInfo[];
}
```

- [ ] **Step 2: Add API methods to game.service.ts**

Read the current file. Add:

```typescript
  getSystemList(galaxy: number) {
    return this.http.get<SystemInfo[]>(`${environment.apiUrl}/game/galaxies/${galaxy}/systems`);
  }

  getSystemDetail(galaxy: number, systemId: number) {
    return this.http.get<SystemDetail>(`${environment.apiUrl}/game/galaxies/${galaxy}/systems/${systemId}`);
  }
```

Update import:
```typescript
import { ..., SystemInfo, SlotInfo, SystemDetail } from '../models/models';
```

- [ ] **Step 3: Add route**

Read `frontend/src/app/app.routes.ts`. Add before the catch-all:
```typescript
  { path: 'galaxy', loadComponent: () => import('./galaxy/galaxy.component').then(m => m.GalaxyComponent), canActivate: [AuthGuard] },
```

- [ ] **Step 4: Add nav link**

Read `frontend/src/app/app.component.ts`. Add after the Fleet link:
```html
        <a routerLink="/galaxy" routerLinkActive="active">Galaxy</a>
```

- [ ] **Step 5: Verify frontend build**

Run: `npx ng build --configuration production 2>&1 | tail -10`
Expected: Should fail because GalaxyComponent doesn't exist yet — but verify the route/nav changes at least parse correctly.

---

### Task 4: Frontend — GalaxyComponent

**Files:**
- Create: `frontend/src/app/galaxy/galaxy.component.ts`

- [ ] **Step 1: Create GalaxyComponent**

```typescript
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { GameService } from '../core/services/game.service';
import { SystemInfo, SlotInfo } from '../core/models/models';

@Component({
  selector: 'app-galaxy',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="galaxy-container">
      <h2>Galaxy Map</h2>

      <!-- Breadcrumb -->
      <div class="breadcrumb" *ngIf="selectedGalaxy">
        <span (click)="backToGalaxies()" class="crumb-link">Galaxies</span>
        <span *ngIf="selectedSystem === null"> &gt; Galaxy {{ selectedGalaxy }}</span>
        <ng-container *ngIf="selectedSystem !== null">
          <span> &gt; </span>
          <span (click)="backToSystems()" class="crumb-link">Galaxy {{ selectedGalaxy }}</span>
          <span> &gt; System {{ selectedSystem }}</span>
        </ng-container>
      </div>

      <!-- Level 1: Galaxy Grid -->
      <div *ngIf="selectedGalaxy === null" class="level">
        <h3>Select Galaxy</h3>
        <div class="galaxy-grid">
          <div *ngFor="let g of galaxyNumbers" class="galaxy-tile"
               [style.background]="getGalaxyColor(g)"
               (click)="selectGalaxy(g)">
            {{ g }}
          </div>
        </div>
      </div>

      <!-- Level 2: System List -->
      <div *ngIf="selectedGalaxy !== null && selectedSystem === null" class="level">
        <h3>Galaxy {{ selectedGalaxy }} — Systems</h3>
        <div class="system-list">
          <div class="system-header">
            <span class="sys-id">System</span>
            <span class="sys-planets">Planets</span>
            <span class="sys-status">Status</span>
          </div>
          <div *ngFor="let sys of systemList" class="system-row"
               [class.own-planet]="sys.hasOwnPlanet"
               (click)="selectSystem(sys.systemId)">
            <span class="sys-id">{{ sys.systemId }}</span>
            <span class="sys-planets">{{ sys.planetCount }} / 15</span>
            <span class="sys-status" [class.occupied]="sys.planetCount > 0">
              {{ sys.hasOwnPlanet ? 'Your Colony' : (sys.planetCount > 0 ? 'Inhabited' : 'Empty') }}
            </span>
          </div>
        </div>
        <div *ngIf="systemList.length === 0" class="empty">Loading...</div>
      </div>

      <!-- Level 3: System Detail -->
      <div *ngIf="selectedGalaxy !== null && selectedSystem !== null" class="level">
        <h3>Galaxy {{ selectedGalaxy }} : System {{ selectedSystem }}</h3>
        <div class="slot-grid">
          <div *ngFor="let s of slots" class="slot-tile"
               [class.own]="s.isOwn"
               [class.enemy]="s.occupied && !s.isOwn"
               [class.has-debris]="s.debrisMetal > 0 || s.debrisCrystal > 0"
               [class.empty-slot]="!s.occupied"
               (click)="clickSlot(s)">
            <div class="slot-number">Slot {{ s.slot }}</div>
            <div *ngIf="!s.occupied" class="slot-empty">Empty</div>
            <ng-container *ngIf="s.occupied">
              <div class="slot-name">{{ s.planetName }}</div>
              <div class="slot-player">{{ s.isOwn ? 'You' : s.playerName }}</div>
              <div class="slot-stats" *ngIf="s.fleetCount > 0 || s.defenseCount > 0">
                F: {{ s.fleetCount }} | D: {{ s.defenseCount }}
              </div>
              <div class="slot-debris" *ngIf="s.debrisMetal > 0 || s.debrisCrystal > 0">
                ☉ {{ s.debrisMetal.toLocaleString() }} / {{ s.debrisCrystal.toLocaleString() }}
              </div>
            </ng-container>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .galaxy-container { padding: 20px; color: #ccc; }
    h2 { color: #ffd700; margin: 0 0 8px 0; }
    h3 { color: #ffd700; margin: 0 0 12px 0; font-size: 14px; }
    .breadcrumb { font-size: 12px; color: #888; margin-bottom: 16px; }
    .crumb-link { color: #4af; cursor: pointer; }
    .crumb-link:hover { text-decoration: underline; }
    .level { background: #1a1a2e; border: 1px solid #333; border-radius: 8px; padding: 16px; }

    .galaxy-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; max-width: 400px; margin: 0 auto; }
    .galaxy-tile { aspect-ratio: 1; display: flex; align-items: center; justify-content: center;
      font-size: 32px; font-weight: bold; border-radius: 12px; cursor: pointer; border: 2px solid transparent;
      transition: transform .1s, border-color .1s; }
    .galaxy-tile:hover { transform: scale(1.05); border-color: #4af; }

    .system-list { font-size: 13px; }
    .system-header { display: flex; padding: 6px 8px; background: #222; border: 1px solid #333; font-weight: bold; color: #ffd700; }
    .system-row { display: flex; padding: 6px 8px; border-bottom: 1px solid #222; cursor: pointer; }
    .system-row:hover { background: #222; }
    .system-row.own-planet { background: #0a2a1a; border-left: 3px solid #4a9; }
    .sys-id { width: 80px; color: #4af; }
    .sys-planets { width: 100px; }
    .sys-status { flex: 1; }
    .sys-status.occupied { color: #4a9; }

    .slot-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; }
    .slot-tile { background: #1a1a2e; border: 1px solid #333; border-radius: 8px; padding: 10px;
      text-align: center; font-size: 12px; cursor: pointer; transition: transform .1s; }
    .slot-tile:hover { transform: scale(1.05); }
    .slot-tile.own { background: #0a2a1a; border-color: #4a9; }
    .slot-tile.enemy { background: #2a1a1a; border-color: #f44; }
    .slot-tile.has-debris { border-color: #fa0; }
    .slot-tile.empty-slot { border-style: dashed; cursor: pointer; }
    .slot-tile.empty-slot:hover { border-color: #4a9; }
    .slot-number { color: #666; font-size: 10px; margin-bottom: 4px; }
    .slot-empty { color: #666; font-style: italic; font-size: 11px; }
    .slot-name { color: #fff; font-weight: bold; font-size: 13px; }
    .slot-player { color: #ffd700; font-size: 11px; }
    .slot-stats { color: #888; font-size: 10px; margin-top: 2px; }
    .slot-debris { color: #fa0; font-size: 10px; }

    .empty { color: #666; font-style: italic; font-size: 13px; padding: 20px; text-align: center; }
  `]
})
export class GalaxyComponent implements OnInit {
  galaxyNumbers = [1,2,3,4,5,6,7,8,9];
  selectedGalaxy: number | null = null;
  selectedSystem: number | null = null;
  systemList: SystemInfo[] = [];
  slots: SlotInfo[] = [];

  constructor(
    private gameService: GameService,
    private router: Router
  ) {}

  ngOnInit() {
    // Start at galaxy 1 by default, or parse from route params
    this.selectGalaxy(1);
  }

  getGalaxyColor(g: number): string {
    const colors = [
      '#1a3a5c', '#1a2a3c', '#2a1a2c', '#2a2a1c', '#1a3a2c',
      '#3a1a1c', '#1a2a3c', '#2a1a3c', '#3a2a1c'
    ];
    return colors[(g - 1) % colors.length];
  }

  selectGalaxy(g: number) {
    this.selectedGalaxy = g;
    this.selectedSystem = null;
    this.systemList = [];
    this.slots = [];
    this.gameService.getSystemList(g).subscribe(list => {
      this.systemList = list;
    });
  }

  selectSystem(systemId: number) {
    this.selectedSystem = systemId;
    this.slots = [];
    this.gameService.getSystemDetail(this.selectedGalaxy!, systemId).subscribe(detail => {
      this.slots = detail.slots;
    });
  }

  backToGalaxies() {
    this.selectedGalaxy = null;
    this.selectedSystem = null;
    this.systemList = [];
    this.slots = [];
  }

  backToSystems() {
    this.selectedSystem = null;
    this.slots = [];
  }

  clickSlot(slot: SlotInfo) {
    if (!slot.occupied) {
      // Colonize
      this.router.navigate(['/fleet'], {
        queryParams: {
          galaxy: this.selectedGalaxy,
          systemId: this.selectedSystem,
          slot: slot.slot,
          mission: 'COLONIZE'
        }
      });
    } else if (!slot.isOwn) {
      // Attack
      this.router.navigate(['/fleet'], {
        queryParams: {
          targetPlanetId: slot.planetId,
          mission: 'ATTACK'
        }
      });
    }
    // Own planet — no action
  }
}
```

- [ ] **Step 2: Verify frontend build**

Run: `npx ng build --configuration production 2>&1 | tail -10`
Expected: Application bundle generation complete

- [ ] **Step 3: Run tests**

Run: `npx ng test --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`
Expected: 1 SUCCESS

---

### Task 5: Final verification

- [ ] **Step 1: Backend compile**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`

- [ ] **Step 2: Frontend build**

Run: `npx ng build --configuration production 2>&1 | tail -5`

- [ ] **Step 3: Frontend tests**

Run: `npx ng test --watch=false --browsers=ChromeHeadless 2>&1 | tail -5`
