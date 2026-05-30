# Phase 7b-a: Core Dark Matter

## Overview

Add dark matter premium currency to game-service: per-player balance, purchase/earn mechanics, spend on speed-ups for construction, research, and shipyard queues. Frontend display in resource bar with speed-up buttons.

## Approaches Considered

Three approaches evaluated:
- **A: Big Bang** — everything (quests, commanders, store) in one phase. Rejected for scope.
- **B: Core DM → Quests → Commanders (chosen)** — Phase 7b-a ships DM display + speed-ups fast, 7b-b adds quests, 7b-c adds commanders.
- **C: Quests first** — earning engine before spending. Rejected: no way to spend DM.

## Cross-Service Design

Dark matter lives on `Player` in auth-service. To avoid cross-service HTTP calls, game-service gets its own `player_resource` table with a `dark_matter` column. The auth-service syncs on player creation; game-service owns runtime mutations.

## Migration V9

New table in game-service:

```sql
CREATE TABLE player_resource (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL UNIQUE,
    dark_matter INT NOT NULL DEFAULT 0
);
```

`PlayerResource` entity + repository.

## Backend Architecture

### PlayerResource (new entity)

- `id` (Long, auto)
- `playerId` (Long, unique)
- `darkMatter` (int, default 0)

### PlayerResourceRepository (new)

- `findByPlayerId(Long playerId) -> Optional<PlayerResource>`

### DarkMatterService (new, game-service)

| Method | Purpose |
|---|---|
| `getDarkMatter(Long playerId) -> int` | Returns current balance |
| `addDarkMatter(Long playerId, int amount)` | Add DM (earn/purchase), creates PlayerResource if missing |
| `spendDarkMatter(Long playerId, int amount) -> boolean` | Deducts DM, returns false if insufficient |

### Speed-Up Cost Formula

- `cost = max(1, ceil(remainingSeconds / 1800))` — 1 DM per 30 minutes
- Speed-up bypass: set `completesAt = Instant.now()` on the queue item
- No refunds for partial speed-ups

### API Endpoints (game-service, under /api/game)

```
GET  /players/{playerId}/dark-matter               → { darkMatter: number }
POST /players/{playerId}/dark-matter/add             → body: { amount: number }
POST /planets/{planetId}/buildings/queue/{queueId}/speed-up → completes construction
POST /planets/{planetId}/shipyard/{queueId}/speed-up → completes ship/defense build
POST /technologies/speed-up                          → body: { technology: string } — completes research
```

### Service Changes

- **BuildingService**: add `speedUpConstruction(Long queueId)` — sets completesAt to now, saves
- **ShipyardService**: add `speedUpShipyardEntry(Long queueId)` — same pattern
- **ResearchService**: add `speedUpResearch(Long queueId)` — same pattern
- **FleetService**: no changes (no DM spending on fleet yet)

## Frontend

### ResourceBarComponent

- Add dark matter display at the end of the resource bar:
  - Purple diamond icon + amount
  - No rate/cap display (DM has no production)

### Speed-Up Buttons

- **OverviewComponent**: On each ConstructionQueue item, show "Speed Up (X)" button
- **ResearchComponent**: On each active research, show "Speed Up (X)" button
- **ShipyardComponent**: On each shipyard queue item, show "Speed Up (X)" button
- Click calls the corresponding endpoint, refreshes queue on success
- Button disabled if remaining <= 0 or insufficient DM

### Frontend Service

New methods in `GameService`:
- `getDarkMatter(playerId: number)`
- `addDarkMatter(playerId: number, amount: number)`
- `speedUpBuilding(planetId: number, queueId: number)`
- `speedUpShipyard(planetId: number, queueId: number)`
- `speedUpResearch(technology: string)`

## Testing

- DarkMatterService: get/add/spend with varying balances
- Speed-up: verify queue item completes immediately
- Cost formula: verify correct DM cost for various remaining times
- Frontend: buttons visible only when queue items exist, disabled when DM insufficient

## Future Phases

- **7b-b**: Achievements + daily quests (earn DM through gameplay)
- **7b-c**: Commanders, resource packs, premium store
