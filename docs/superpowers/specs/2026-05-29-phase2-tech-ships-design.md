# Phase 2: Tech Tree + Ships Design

## Overview

Phase 2 adds two gameplay subsystems to the existing building construction:
- **Technology System**: Players research technologies at their Research Lab. Technologies provide passive bonuses globally (per-player). Only one research can be active per player.
- **Ship System**: Players build ships at their Shipyard. Ships accumulate on the planet. No fleets, movement, or combat yet (Phase 3).

## Technology System

### Data Model

**`technology` table** (new, Flyway V4):
```sql
CREATE TABLE player_technology (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    technology VARCHAR(32) NOT NULL,
    level INT NOT NULL DEFAULT 0,
    UNIQUE (player_id, technology)
);
```

**`research_queue` table** (new, Flyway V4):
```sql
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

One active research per player — enforced by application logic in the service layer.

### Technologies

All per-player, start at level 0:

| Technology | Prerequisites | Cost Formula (next level) | Base Time | Effect (+per level) |
|---|---|---|---|---|
| Energy Tech | none | `M: 200*2^L, C: 100*2^L` | 600s | +5% solar/fusion output |
| Laser Tech | none | `M: 100*2^L, C: 50*2^L` | 400s | +5% laser weapon attack |
| Ion Tech | none | `M: 250*2^L, C: 150*2^L` | 800s | +5% ion weapon attack |
| Plasma Tech | Energy 5, Laser 5 | `M: 500*2^L, C: 300*2^L, G: 100*2^L` | 2000s | +5% plasma weapon attack |
| Combustion Drive | Energy 1 | `M: 200*2^L, C: 100*2^L` | 600s | +10% combustion engine speed |
| Impulse Drive | Combustion 5, Energy 2 | `M: 1000*2^L, C: 500*2^L, G: 200*2^L` | 1800s | +10% impulse engine speed |
| Hyperspace Drive | Impulse 5, Energy 3 | `M: 2000*2^L, C: 1000*2^L, G: 500*2^L` | 3600s | +10% hyperspace engine speed |
| Weapon Tech | Laser 3 | `M: 400*2^L, C: 200*2^L` | 1200s | +5% all weapon attack |
| Shielding Tech | Energy 3 | `M: 200*2^L, C: 400*2^L` | 1200s | +5% shield strength |
| Armor Tech | none | `M: 200*2^L, C: 100*2^L` | 600s | +5% hull strength |
| Computer Tech | none | `M: 100*2^L, C: 200*2^L` | 400s | +1 fleet slot (Phase 3) |
| Espionage Tech | Computer 3 | `M: 200*2^L, C: 400*2^L, G: 100*2^L` | 1200s | +1 probe range (Phase 3) |
| Graviton Tech | Energy 10, Plasma 5 | `M: 5000*2^L, C: 5000*2^L, G: 1000*2^L` | 14400s | Required for Death Star (Phase 3) |

Where `L = current level` (so level 0→1 uses L=0, 1→2 uses L=1).

### Research Time

```
researchTime = baseTime * 2^targetLevel / (1 + researchLabLevel) * speed
```

The Research Lab building level used is the **highest level across all planets owned by the player** (grid position 8). The `game.speed` config applies as a divisor.

### Effects

Technology effects apply globally per-player. They modify game formulas in `GameBalancer`:
- Energy boosting solar/fusion output: `solar * (1 + 0.05 * energyTechLevel)`
- Weapon boosting ship attack: `baseAttack * (1 + 0.05 * weaponTechLevel)`
- Armor boosting ship hull: `baseHull * (1 + 0.05 * armorTechLevel)`
- Etc.

### API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/game/technologies` | List player's technology levels with prerequisites |
| GET | `/api/game/technologies/{name}` | Get technology details (cost, time for next level) |
| POST | `/api/game/technologies/{name}/research` | Start researching next level |
| GET | `/api/game/planets/{id}/research-queue` | Active research queue for this player |

## Ship System

### Ship Types

Defined as an enum `ShipType` in the domain package:

| Ship | Metal Cost | Crystal Cost | Gas Cost | Build Time | Prerequisites |
|------|-----------|-------------|---------|-----------|---------------|
| Light Fighter | 500 | 100 | 0 | 120s | Shipyard 1 |
| Heavy Fighter | 2500 | 500 | 0 | 360s | Shipyard 3, Armor 2 |
| Cruiser | 5000 | 2000 | 1000 | 1200s | Shipyard 5, Impulse 3 |
| Battleship | 15000 | 5000 | 3000 | 3600s | Shipyard 7, Hyperspace 5 |
| Small Cargo | 1000 | 500 | 0 | 240s | Shipyard 2 |
| Large Cargo | 3000 | 1500 | 0 | 600s | Shipyard 4, Combustion 6 |
| Colony Ship | 5000 | 2500 | 5000 | 2400s | Shipyard 5, Impulse 3 |
| Recycler | 2000 | 1000 | 500 | 600s | Shipyard 3, Combustion 3 |
| Espionage Probe | 100 | 50 | 0 | 30s | Shipyard 1, Computer 2 |

Cost formula for ships: fixed cost per ship (not level-based). Base costs are shown above. `game.speed` applies.

### Build Time

```
buildTime = baseTime * speed / (1 + shipyardLevel + naniteFactoryLevel)
```

### Data Model

**`planet_ship` table** (new, Flyway V5):
```sql
CREATE TABLE planet_ship (
    id BIGSERIAL PRIMARY KEY,
    planet_id BIGINT NOT NULL,
    ship_type VARCHAR(32) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    UNIQUE (planet_id, ship_type)
);
```

**`shipyard_queue` table** (new, Flyway V5):
```sql
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

Each queue entry builds a single type of ship in quantity. Resources are deducted on queue start.

### API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/game/planets/{id}/ships` | List ships on a planet |
| POST | `/api/game/planets/{id}/ships/{type}/build` | Queue ship construction |
| GET | `/api/game/planets/{id}/shipyard-queue` | Get shipyard queue |
| GET | `/api/game/ships` | List all ship types with costs |

## Game Loop Changes

The `GameLoopService` gets two new processors alongside the existing construction processor:

1. **Research completion**: Find `research_queue` with `completed=false AND completes_at <= now`, apply the technology level, mark complete, push WS message
2. **Shipyard completion**: Find `shipyard_queue` with `completed=false AND completes_at <= now`, add ships to `planet_ship`, mark complete, push WS message

## Frontend

### New Routes
| Route | Component | Purpose |
|-------|-----------|---------|
| `/research` | ResearchComponent | Technology tree with tech cards + active research |
| `/shipyard` | ShipyardComponent | Ship building with quantity selector |

### New Models (`models.ts`)
```typescript
Technology { name, level, displayName, description, metalCost, crystalCost, gasCost, timeSeconds, researchLabLevelNeeded, prerequisites, canResearch }
ShipType { name, displayName, description, metalCost, crystalCost, gasCost, timeSeconds, shipyardLevelNeeded }
```

### Research View
- Grid of technology cards showing current level, next level cost/time
- Greyed out if prerequisites not met
- "Research" button starts next level
- Active research shown with progress bar
- WebSocket subscription to `/topic/research/{playerId}` for completion notifications

### Shipyard View
- List of buildable ships per planet
- Quantity input + "Build" button
- Shipyard queue display with ETA
- WebSocket subscription to `/topic/planet/{planetId}` for ship completion (reuses existing topic)

## GameBalancer Additions

New methods:
- `getTechnologyCost(name, level)` → metal, crystal, gas
- `getResearchTimeSeconds(name, level, researchLabLevel)` → seconds
- `getShipCost(type)` → metal, crystal, gas
- `getShipBuildTimeSeconds(type, shipyardLevel, naniteFactoryLevel)` → seconds
- `getTechnologyEffect(name, level)` → multiplier
- `getShipBaseStats(type)` → hull, shield, attack, speed, cargo (for Phase 3)

## Files to Create/Modify

**Backend (new):**
- `domain/Technology.java` — enum of technology types
- `domain/ShipType.java` — enum of ship types
- `domain/PlayerTechnology.java` — JPA entity
- `domain/ResearchQueue.java` — JPA entity
- `domain/PlanetShip.java` — JPA entity
- `domain/ShipyardQueue.java` — JPA entity
- `service/ResearchService.java` — research logic
- `service/ShipyardService.java` — ship building logic
- `web/TechnologyController.java` — REST + WS endpoints
- `web/ShipyardController.java` — REST + WS endpoints
- `repository/PlayerTechnologyRepository.java`
- `repository/ResearchQueueRepository.java`
- `repository/PlanetShipRepository.java`
- `repository/ShipyardQueueRepository.java`

**Backend (modify):**
- `service/GameBalancer.java` — add tech/ship formulas
- `service/GameLoopService.java` — add research + shipyard processors
- `config/WebSocketConfig.java` — ensure `/topic` prefix for new topics

**Database:**
- `src/main/resources/db/migration/V4__create_technology_and_research.sql`
- `src/main/resources/db/migration/V5__create_shipyard.sql`

**Frontend (new):**
- `src/app/research/research.component.ts` + `.html` + `.css`
- `src/app/shipyard/shipyard.component.ts` + `.html` + `.css`

**Frontend (modify):**
- `src/app/app.routes.ts` — add research + shipyard routes
- `src/app/models.ts` — add Technology, ShipType interfaces
- `src/app/services/game.service.ts` — add tech/ship API methods
- `src/app/app.component.html` — add nav links for new routes
