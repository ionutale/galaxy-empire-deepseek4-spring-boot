# Phase 6: Galaxy Map

## Overview

3-level galaxy navigation: Galaxy Selection → System List → System Detail (15 slots). Enables planet discovery and fleet target selection.

## Backend

New `GalaxyController` at `/api/game/galaxies/`:

| Endpoint | Returns | Notes |
|----------|---------|-------|
| `GET /api/game/galaxies/{galaxy}/systems?page=0&size=50` | `{ content: [...], totalPages, totalElements }` | Paginated system list with per-system planet counts |
| `GET /api/game/galaxies/{galaxy}/systems/{systemId}` | `SystemDetail` with 15 slots | Full slot info for the system view |

**SystemListEntry** (per system):
```json
{
  "systemId": 234,
  "planetCount": 5,
  "occupiedSlots": 5,
  "hasOwnPlanet": true
}
```

**SystemDetail structure per slot:**
```json
{
  "slots": [
    {
      "slot": 1,
      "occupied": true,
      "planetId": 42,
      "planetName": "Home Planet",
      "playerName": "Player1",
      "playerId": 1,
      "isOwn": true,
      "fleetCount": 120,
      "defenseCount": 45,
      "debrisMetal": 0,
      "debrisCrystal": 0
    },
    { "slot": 2, "occupied": false },
    ...
  ]
}
```

No new DB tables — queries `planet`, `planet_ship` (sum by planet), `planet_defense` (sum by planet), `debris_field`.

## Frontend

Single new route `/galaxy` with lazy-loaded `GalaxyComponent`.

### Levels

1. **Galaxy Grid**: 9 clickable tiles (2+7 or 3×3). Color by activity (# of occupied slots in galaxy). Click → system list.

2. **System List**: Paginated table. Columns: System#, Planet Count, Activity. Your colonies highlighted in green. Click → system detail.

3. **Slot Grid**: 15 slots rendered 5×3. Tile states:
   - **Empty**: gray border, "Empty", clickable for colonize
   - **Your planet**: green border, planet name, "You", F/D counts
   - **Enemy planet**: red border, planet name, player name, F/D counts
   - **Debris field**: orange border, metal/crystal amounts
   - **Inactive/abandoned**: dimmed, occupied but inactive

System activity indicators (v1): planetCount > 0 → "Inhabited", hasOwnPlanet → highlighted green, planetCount = 0 → "Empty"

### Navigation

Breadcrumb: `Galaxy {N} > System {M}`. Back buttons at each level.

### Integration

Clicking an enemy planet or empty slot on the Slot Grid navigates to `/fleet` with query params `?targetPlanetId=X&mission=ATTACK` (or `?galaxy=X&systemId=Y&slot=Z&mission=COLONIZE`).

## API Methods

Add to `game.service.ts`:
- `getSystemList(galaxy, page, size)` → paginated systems
- `getSystemDetail(galaxy, systemId)` → slot data

## Frontend Interfaces

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
