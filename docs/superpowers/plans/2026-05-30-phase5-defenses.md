# Phase 5: Planetary Defenses Implementation Plan

> **For agentic workers:** Subagent-driven development. Steps use checkbox syntax.

**Goal:** Add planetary defense structures that protect planets during combat

**Architecture:** Defenses are stationary combat units built via ShipyardQueue, stored in `planet_defense` table, and resolved in CombatService before ships. ShipyardQueue gets a nullable `defense_type` column to support both ship and defense building.

---

### Task 1: DefenseType enum + V8 migration + PlanetDefense entity + repo

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/DefenseType.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/PlanetDefense.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/PlanetDefenseRepository.java`
- Create: `backend/game-service/src/main/resources/db/migration/V8__add_defenses.sql`
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/domain/ShipyardQueue.java`

- [ ] **Step 1: Create DefenseType enum**

```java
package com.galaxyempire.game.domain;

public enum DefenseType {
    ROCKET_LAUNCHER, LIGHT_LASER, HEAVY_LASER, ION_CANNON, PLASMA_TURRET, SMALL_SHIELD, LARGE_SHIELD
}
```

- [ ] **Step 2: Create PlanetDefense entity**

```java
package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "planet_defense", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"planet_id", "defense_type"})
})
public class PlanetDefense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planet_id", nullable = false)
    private Long planetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "defense_type", nullable = false, length = 32)
    private DefenseType defenseType;

    @Column(nullable = false)
    private int quantity = 0;

    public PlanetDefense() {}

    public PlanetDefense(Long planetId, DefenseType defenseType) {
        this.planetId = planetId;
        this.defenseType = defenseType;
        this.quantity = 0;
    }

    public Long getId() { return id; }
    public Long getPlanetId() { return planetId; }
    public DefenseType getDefenseType() { return defenseType; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void addQuantity(int amount) { this.quantity += amount; }
}
```

- [ ] **Step 3: Create PlanetDefenseRepository**

```java
package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.PlanetDefense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlanetDefenseRepository extends JpaRepository<PlanetDefense, Long> {
    List<PlanetDefense> findByPlanetId(Long planetId);
    Optional<PlanetDefense> findByPlanetIdAndDefenseType(Long planetId, com.galaxyempire.game.domain.DefenseType defenseType);
}
```

- [ ] **Step 4: Create V8 migration**

```sql
CREATE TABLE planet_defense (
    id BIGSERIAL PRIMARY KEY,
    planet_id BIGINT NOT NULL,
    defense_type VARCHAR(32) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    UNIQUE(planet_id, defense_type)
);
CREATE INDEX idx_planet_defense_planet ON planet_defense(planet_id);

ALTER TABLE shipyard_queue ADD COLUMN defense_type VARCHAR(32);
ALTER TABLE shipyard_queue ALTER COLUMN ship_type DROP NOT NULL;
```

- [ ] **Step 5: Update ShipyardQueue entity**

Read the current file. Add after `shipType` field:
```java
    @Enumerated(EnumType.STRING)
    @Column(name = "defense_type", length = 32)
    private DefenseType defenseType;
```

Add getter/setter:
```java
    public DefenseType getDefenseType() { return defenseType; }
    public void setDefenseType(DefenseType defenseType) { this.defenseType = defenseType; }
```

Make `shipType` setter accept nullable. Remove `nullable = false` from the @Column annotation on `shipType`:
```java
    @Enumerated(EnumType.STRING)
    @Column(name = "ship_type", length = 32)
    private ShipType shipType;
```

- [ ] **Step 6: Verify compilation**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`

---

### Task 2: GameBalancer defense stats

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/GameBalancer.java`

- [ ] **Step 1: Add defense stat methods**

Add after the ship methods:

```java
    public int getDefenseAttack(DefenseType type) {
        return switch (type) {
            case ROCKET_LAUNCHER -> 80;
            case LIGHT_LASER -> 100;
            case HEAVY_LASER -> 250;
            case ION_CANNON -> 150;
            case PLASMA_TURRET -> 3000;
            case SMALL_SHIELD, LARGE_SHIELD -> 1;
        };
    }

    public int getDefenseShield(DefenseType type) {
        return switch (type) {
            case ROCKET_LAUNCHER -> 20;
            case LIGHT_LASER -> 25;
            case HEAVY_LASER -> 100;
            case ION_CANNON -> 500;
            case PLASMA_TURRET -> 300;
            case SMALL_SHIELD -> 2000;
            case LARGE_SHIELD -> 10000;
        };
    }

    public int getDefenseHull(DefenseType type) {
        return switch (type) {
            case ROCKET_LAUNCHER -> 200;
            case LIGHT_LASER -> 200;
            case HEAVY_LASER -> 800;
            case ION_CANNON -> 800;
            case PLASMA_TURRET -> 2000;
            case SMALL_SHIELD -> 2000;
            case LARGE_SHIELD -> 10000;
        };
    }

    public double getDefenseMetalCost(DefenseType type) {
        return switch (type) {
            case ROCKET_LAUNCHER -> 2000;
            case LIGHT_LASER -> 1500;
            case HEAVY_LASER -> 6000;
            case ION_CANNON -> 2000;
            case PLASMA_TURRET -> 50000;
            case SMALL_SHIELD -> 10000;
            case LARGE_SHIELD -> 50000;
        };
    }

    public double getDefenseCrystalCost(DefenseType type) {
        return switch (type) {
            case ROCKET_LAUNCHER -> 0;
            case LIGHT_LASER -> 500;
            case HEAVY_LASER -> 2000;
            case ION_CANNON -> 6000;
            case PLASMA_TURRET -> 50000;
            case SMALL_SHIELD -> 10000;
            case LARGE_SHIELD -> 50000;
            default -> 0;
        };
    }

    public double getDefenseGasCost(DefenseType type) {
        return switch (type) {
            case PLASMA_TURRET -> 30000;
            default -> 0;
        };
    }

    public int getDefenseBuildTimeSeconds(DefenseType type, double shipyardLevel, double naniteLevel) {
        int base = switch (type) {
            case ROCKET_LAUNCHER -> 300;
            case LIGHT_LASER -> 240;
            case HEAVY_LASER -> 600;
            case ION_CANNON -> 1200;
            case PLASMA_TURRET -> 7200;
            case SMALL_SHIELD -> 1200;
            case LARGE_SHIELD -> 7200;
        };
        return (int) Math.ceil(base * 1.0 / (1 + shipyardLevel + naniteLevel));
    }

    public int getRequiredShipyardLevelForDefense(DefenseType type) {
        return switch (type) {
            case ROCKET_LAUNCHER -> 1;
            case LIGHT_LASER -> 2;
            case SMALL_SHIELD -> 3;
            case HEAVY_LASER -> 4;
            case ION_CANNON -> 5;
            case LARGE_SHIELD -> 6;
            case PLASMA_TURRET -> 8;
        };
    }
```

- [ ] **Step 2: Verify compilation**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`

---

### Task 3: ShipyardService buildDefense

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/ShipyardService.java`

- [ ] **Step 1: Add buildDefense method**

Read the current file. Add this method after `buildShips`:

```java
    @Transactional
    public Map<String, Object> buildDefense(Long planetId, DefenseType defenseType, int quantity, Long playerId) {
        var planet = planetRepository.findById(planetId)
            .orElseThrow(() -> new IllegalArgumentException("Planet not found: " + planetId));
        if (!planet.getPlayerId().equals(playerId)) {
            throw new IllegalArgumentException("Planet does not belong to player");
        }

        int shipyardLevel = buildingRepository
            .findByPlanetIdAndGridPosition(planetId, 9)
            .map(Building::getLevel)
            .orElse(0);
        if (shipyardLevel < gameBalancer.getRequiredShipyardLevelForDefense(defenseType)) {
            throw new IllegalArgumentException("Shipyard level too low for " + defenseType);
        }

        double metalCost = gameBalancer.getDefenseMetalCost(defenseType) * quantity;
        double crystalCost = gameBalancer.getDefenseCrystalCost(defenseType) * quantity;
        double gasCost = gameBalancer.getDefenseGasCost(defenseType) * quantity;

        if (planet.getMetal() < metalCost || planet.getCrystal() < crystalCost || planet.getGas() < gasCost) {
            throw new IllegalArgumentException("Insufficient resources");
        }

        planet.setMetal(planet.getMetal() - metalCost);
        planet.setCrystal(planet.getCrystal() - crystalCost);
        planet.setGas(planet.getGas() - gasCost);
        planetRepository.save(planet);

        int timeSeconds = gameBalancer.getDefenseBuildTimeSeconds(defenseType, shipyardLevel, 0);

        var queue = new ShipyardQueue();
        queue.setPlanetId(planetId);
        queue.setDefenseType(defenseType);
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
        result.put("defenseType", defenseType.name());
        result.put("quantity", quantity);
        result.put("completesAt", queue.getCompletesAt().toString());
        result.put("remainingSeconds", timeSeconds);
        return result;
    }
```

Add import for DefenseType:
```java
import com.galaxyempire.game.domain.DefenseType;
```

- [ ] **Step 2: Add getDefenseTypes and getPlanetDefenses read methods**

```java
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDefenseTypes(Long planetId) {
        int shipyardLevel = buildingRepository
            .findByPlanetIdAndGridPosition(planetId, 9)
            .map(Building::getLevel)
            .orElse(0);

        List<Map<String, Object>> result = new ArrayList<>();
        for (DefenseType type : DefenseType.values()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("defenseType", type.name());
            entry.put("metalCost", gameBalancer.getDefenseMetalCost(type));
            entry.put("crystalCost", gameBalancer.getDefenseCrystalCost(type));
            entry.put("gasCost", gameBalancer.getDefenseGasCost(type));
            entry.put("timeSeconds", gameBalancer.getDefenseBuildTimeSeconds(type, shipyardLevel, 0));
            entry.put("requiredShipyardLevel", gameBalancer.getRequiredShipyardLevelForDefense(type));
            entry.put("available", shipyardLevel >= gameBalancer.getRequiredShipyardLevelForDefense(type));
            result.add(entry);
        }
        return result;
    }
```

- [ ] **Step 3: Update completeShipyardEntry to handle defenses**

In `completeShipyardEntry`, after the planetShip block, add:
```java
        if (queue.getDefenseType() != null) {
            var planetDefense = planetDefenseRepository
                .findByPlanetIdAndDefenseType(queue.getPlanetId(), queue.getDefenseType())
                .orElseGet(() -> planetDefenseRepository.save(new PlanetDefense(queue.getPlanetId(), queue.getDefenseType())));
            planetDefense.addQuantity(queue.getQuantity());
            planetDefenseRepository.save(planetDefense);
            return;
        }
```

Add PlanetDefenseRepository dependency:
```java
    private final PlanetDefenseRepository planetDefenseRepository;
```

Add to constructor:
```java
    public ShipyardService(PlanetShipRepository planetShipRepository,
                           ShipyardQueueRepository shipyardQueueRepository,
                           BuildingRepository buildingRepository,
                           PlanetRepository planetRepository,
                           GameBalancer gameBalancer,
                           PlanetDefenseRepository planetDefenseRepository) {
        ...
        this.planetDefenseRepository = planetDefenseRepository;
    }
```

Need import: `import com.galaxyempire.game.domain.PlanetDefense;` (already covered by domain.* if using wildcard).

- [ ] **Step 4: Add ShipyardController defense endpoints (or extend existing)**

Read `backend/game-service/src/main/java/com/galaxyempire/game/web/ShipyardController.java`. If it exists, add:
```java
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
```

Need to add `getPlanetDefenses` to ShipyardService:
```java
    @Transactional(readOnly = true)
    public List<PlanetDefense> getPlanetDefenses(Long planetId) {
        return planetDefenseRepository.findByPlanetId(planetId);
    }
```

- [ ] **Step 5: Verify compilation**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -10`

---

### Task 4: GameLoopService defense completion

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/GameLoopService.java`

- [ ] **Step 1: Read current file and verify defense completion is already handled**

The GameLoopService calls `shipyardService.completeShipyardEntry(queue.getId())` for completed ShipyardQueue entries. Since we modified `completeShipyardEntry` to handle defense_type, no changes should be needed. Verify by reading the file.

If no changes needed, skip this task.

---

### Task 5: CombatService defense integration

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/CombatService.java`

- [ ] **Step 1: Add PlanetDefenseRepository dependency**

```java
    private final PlanetDefenseRepository planetDefenseRepository;
```

Add to constructor:
```java
                      PlanetDefenseRepository planetDefenseRepository) {
    ...
    this.planetDefenseRepository = planetDefenseRepository;
}
```

- [ ] **Step 2: Load defenses at start of resolveCombat**

In resolveCombat, after `List<PlanetShip> defenderShips = planetShipRepository.findByPlanetId(targetPlanetId);`, add:
```java
        List<PlanetDefense> defenderDefenses = planetDefenseRepository.findByPlanetId(targetPlanetId);
```

- [ ] **Step 3: Add defense resolution phase before ship combat**

After the `defenderBefore` map, add a "defenses fire first" phase:

```java
        // Phase 1: Defenses fire (before ship combat)
        for (PlanetDefense pd : new ArrayList<>(defenderDefenses)) {
            if (pd.getQuantity() <= 0) continue;
            if (pd.getDefenseType() == DefenseType.SMALL_SHIELD || pd.getDefenseType() == DefenseType.LARGE_SHIELD) continue; // shields handled separately
            int attack = gameBalancer.getDefenseAttack(pd.getDefenseType());
            Random rand = new Random();
            for (int i = 0; i < pd.getQuantity(); i++) {
                ShipType targetType = pickRandomTarget(attackerCurrent, rand);
                if (targetType == null) break;
                int shield = gameBalancer.getShipShield(targetType);
                int hull = gameBalancer.getShipHull(targetType);
                int damage = Math.max(0, attack - shield);
                if (damage > 0) {
                    destroyShips(attackerCurrent, targetType, damage, new HashMap<>());
                }
            }
        }
```

Actually, this uses `destroyShips` without capturing losses. Let me think about this more carefully.

The existing combat flow:
1. Attacker ships fire at defender ships
2. Defender ships fire at attacker ships
3. Repeat for up to 6 rounds

With defenses, the flow should be:
1. Defenses fire at attacker ships
2. Attacker ships fire at defender ships + defenses
3. Defender ships fire at attacker ships
4. Repeat for up to 6 rounds

But the current `fireShipGroup` method takes a `List<?>` of targets and uses `instanceof FleetShip` / `instanceof PlanetShip` checks in `pickRandomTarget` and `destroyShips`. Defenses are `PlanetDefense` objects, not `PlanetShip` or `FleetShip`.

The combat service's fire/random/destroy methods only handle FleetShip and PlanetShip types. I need to extend them.

Actually, the cleanest approach: treat defenses like ships for combat purposes. Make a unified approach:

Option A: Create a generic "CombatUnit" abstraction
Option B: Convert defenses to a pseudo-ship format for combat
Option C: Add PlanetDefense handling to the existing typed methods

Option C is simplest. I'll add PlanetDefense cases to `pickRandomTarget` and `destroyShips`, and add `PlanetDefense` to the firing loop.

But defenses are different from ships in the original game:
- Defenses don't get rapid fire
- Both attackers and defenders can target defenses
- Defenses can be repaired after battle (a percentage survives)

Let me simplify: integrate defenses directly into the combat rounds. Each round:
1. Defenses fire at random attacker ships (no rapid fire)
2. Attacker ships fire at random defender ships OR defenses (using existing fireShipGroup but targets include ships AND defenses)
3. Defender ships fire at random attacker ships

Actually, for minimum complexity, let me just add a pre-combat defense salvo and then run the existing combat as-is. This matches the original game mechanics where defenses fire first, then normal combat ensues.

Better approach: just add defense stats to GameBalancer and treat defenses as if they were ships during combat by creating temporary FleetShip-like objects.

Simplest approach that works:

```java
        // Fire defenses at attackers before combat
        for (PlanetDefense pd : defenderDefenses) {
            if (pd.getQuantity() <= 0) continue;
            DefenseType dt = pd.getDefenseType();
            if (dt == DefenseType.SMALL_SHIELD || dt == DefenseType.LARGE_SHIELD) continue;
            for (int i = 0; i < pd.getQuantity(); i++) {
                ShipType target = pickRandomTarget(attackerCurrent, new Random());
                if (target == null) break;
                int shield = gameBalancer.getShipShield(target);
                int hull = gameBalancer.getShipHull(target);
                int attack = gameBalancer.getDefenseAttack(dt);
                int damage = Math.max(0, attack - shield);
                if (damage > 0) {
                    int shipsDestroyed = Math.max(1, damage / hull);
                    for (FleetShip fs : attackerCurrent) {
                        if (fs.getShipType() == target && fs.getQuantity() > 0) {
                            int actual = Math.min(shipsDestroyed, fs.getQuantity());
                            fs.setQuantity(fs.getQuantity() - actual);
                            break;
                        }
                    }
                }
            }
        }
```

Then after combat, I need to compute defense losses (a percentage of defenses are destroyed when attackers fire at them). Actually, in the original game, attacker ships also target defenses during combat. So I need defenses to be part of the target pool.

Hmm, this is getting complex. Let me take a simpler approach for v1:

1. Pre-combat: defenses fire a single salvo at attackers
2. Normal ship combat proceeds (attacker ships vs defender ships)
3. After combat: if attacker wins, a percentage of defenses are destroyed (e.g., 50% survive). If defender wins, all defenses survive.

This is a reasonable simplification for a first implementation. Let me write it this way.

- [ ] **Step 2: Implement defense salvo and post-combat defense destruction**

Add logic after loading defenderDefenses but before the ship combat loop:

```java
        // Phase 1: Defenses fire at attackers
        Map<String, Integer> defenseKills = new HashMap<>();
        for (PlanetDefense pd : defenderDefenses) {
            if (pd.getQuantity() <= 0) continue;
            DefenseType dt = pd.getDefenseType();
            if (dt == DefenseType.SMALL_SHIELD || dt == DefenseType.LARGE_SHIELD) continue;
            for (int i = 0; i < pd.getQuantity(); i++) {
                ShipType target = pickRandomTarget(attackerCurrent, new Random());
                if (target == null) break;
                int shield = gameBalancer.getShipShield(target);
                int damage = Math.max(0, gameBalancer.getDefenseAttack(dt) - shield);
                if (damage > 0) {
                    int hull = gameBalancer.getShipHull(target);
                    int shipsDestroyed = Math.max(1, damage / hull);
                    for (FleetShip fs : attackerCurrent) {
                        if (fs.getShipType() == target && fs.getQuantity() > 0) {
                            int actual = Math.min(shipsDestroyed, fs.getQuantity());
                            fs.setQuantity(fs.getQuantity() - actual);
                            defenseKills.merge(target.name(), actual, Integer::sum);
                            break;
                        }
                    }
                }
            }
        }
```

After all combat rounds resolve (after the round loop), add defense losses:

```java
        // Post-combat: destroy a portion of defenses
        double survivalRate = attackerDefeated ? 1.0 : 0.5;
        for (PlanetDefense pd : defenderDefenses) {
            if (pd.getQuantity() > 0 && pd.getDefenseType() != DefenseType.SMALL_SHIELD && pd.getDefenseType() != DefenseType.LARGE_SHIELD) {
                int destroyed = (int) Math.round(pd.getQuantity() * (1 - survivalRate));
                if (destroyed > 0) {
                    debrisMetal += gameBalancer.getDefenseMetalCost(pd.getDefenseType()) * 0.3 * destroyed;
                    debrisCrystal += gameBalancer.getDefenseCrystalCost(pd.getDefenseType()) * 0.3 * destroyed;
                    pd.setQuantity(pd.getQuantity() - destroyed);
                }
            }
        }
```

Save surviving defenses:
```java
        planetDefenseRepository.deleteAll(defenderDefenses);
        planetDefenseRepository.saveAll(defenderDefenses);
```

- [ ] **Step 3: Verify compilation**

Run compile command.

---

### Task 6: Frontend

**Files:**
- Modify: `frontend/src/app/core/models/models.ts`
- Modify: `frontend/src/app/core/services/game.service.ts`
- Modify: `frontend/src/app/shipyard/shipyard.component.ts`
- Modify: `frontend/src/app/shipyard/shipyard.component.ts`

- [ ] **Step 1: Add DefenseType interface to models.ts**

```typescript
export interface DefenseType {
  defenseType: string;
  metalCost: number;
  crystalCost: number;
  gasCost: number;
  timeSeconds: number;
  requiredShipyardLevel: number;
  available: boolean;
}

export interface PlanetDefense {
  id: number;
  planetId: number;
  defenseType: string;
  quantity: number;
}
```

- [ ] **Step 2: Add API methods to game.service.ts**

```typescript
  getDefenseTypes(planetId: number) {
    return this.http.get<DefenseType[]>(`${environment.apiUrl}/game/planets/${planetId}/defense-types`);
  }

  buildDefense(planetId: number, defenseType: string, quantity: number) {
    return this.http.post(`${environment.apiUrl}/game/planets/${planetId}/defense`, { defenseType, quantity });
  }

  getPlanetDefenses(planetId: number) {
    return this.http.get<PlanetDefense[]>(`${environment.apiUrl}/game/planets/${planetId}/defenses`);
  }
```

Update import:
```typescript
import { ..., DefenseType, PlanetDefense } from '../models/models';
```

- [ ] **Step 3: Extend ShipyardComponent with defense tab**

Read the current file first. Add a `showDefenses` toggle and defense-related fields:

```typescript
  showDefenses = false;
  defenseTypes: DefenseType[] = [];
  planetDefenses: PlanetDefense[] = [];
  selectedDefense: string = '';
  defenseQuantity: number = 1;
```

Add `toggleDefenses()` method and `loadDefenses()`:
```typescript
  toggleDefenses() {
    this.showDefenses = !this.showDefenses;
    if (this.showDefenses) {
      this.loadDefenses();
    }
  }

  loadDefenses() {
    this.gameService.getDefenseTypes(this.planetId).subscribe(types => {
      this.defenseTypes = types;
    });
    this.gameService.getPlanetDefenses(this.planetId).subscribe(defs => {
      this.planetDefenses = defs;
    });
  }

  buildDefense() {
    if (!this.selectedDefense || this.defenseQuantity < 1) return;
    this.gameService.buildDefense(this.planetId, this.selectedDefense, this.defenseQuantity).subscribe({
      next: () => this.loadDefenses(),
      error: (err) => console.error(err)
    });
  }
```

Add defense section to template (inline, after the ships section):
```html
      <div class="section">
        <h3>Defenses</h3>
        <button (click)="toggleDefenses()" class="toggle-btn">
          {{ showDefenses ? 'Hide' : 'Show' }} Defenses
        </button>
        <div *ngIf="showDefenses">
          <div *ngIf="planetDefenses.length > 0" class="defense-list">
            <div *ngFor="let d of planetDefenses" class="defense-row">
              <span>{{ getDisplayName(d.defenseType) }}: {{ d.quantity }}</span>
            </div>
          </div>
          <div *ngIf="planetDefenses.length === 0" class="empty">No defenses built.</div>
          <div class="build-form">
            <div class="form-row">
              <label>Defense Type:</label>
              <select [(ngModel)]="selectedDefense">
                <option value="">Select...</option>
                <option *ngFor="let t of defenseTypes" [value]="t.defenseType" [disabled]="!t.available">
                  {{ getDisplayName(t.defenseType) }} ({{ t.metalCost }}M / {{ t.crystalCost }}C / {{ t.gasCost }}G)
                </option>
              </select>
            </div>
            <div class="form-row">
              <label>Quantity:</label>
              <input type="number" [(ngModel)]="defenseQuantity" min="1">
            </div>
            <button (click)="buildDefense()" [disabled]="!selectedDefense">Build</button>
          </div>
        </div>
      </div>
```

Add styles:
```css
    .toggle-btn { padding: 4px 12px; background: #555; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: 12px; margin-bottom: 8px; }
    .toggle-btn:hover { background: #666; }
    .defense-list { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
    .defense-row { font-size: 13px; color: #ccc; }
    .build-form { display: flex; flex-direction: column; gap: 8px; }
    .build-form select { padding: 4px; background: #222; border: 1px solid #444; color: #fff; border-radius: 4px; }
```

- [ ] **Step 4: Verify frontend build**

Run: `npx ng build --configuration production 2>&1 | tail -10`

- [ ] **Step 5: Run tests**

Run: `npx ng test --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`

---

### Task 7: Final build verification

- [ ] **Step 1: Backend compile**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -10`
Expected: BUILD SUCCESS

- [ ] **Step 2: Frontend build**

Run: `npx ng build --configuration production 2>&1 | tail -10`
Expected: Application bundle generation complete

- [ ] **Step 3: Frontend tests**

Run: `npx ng test --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`
Expected: 1 SUCCESS
