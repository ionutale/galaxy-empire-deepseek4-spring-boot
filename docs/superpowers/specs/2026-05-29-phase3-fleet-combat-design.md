# Phase 3: Fleet Missions & Combat — Design Spec

## Overview

Add fleet system with Attack and Deploy missions, rapid-fire combat, combat reports, and debris fields.

## Architecture

Player launches fleet from one planet to another with ships grouped by type. On arrival, Attack triggers instant combat resolution (in GameLoop tick). Deploy transfers ships between own planets. Combat generates a report and debris. Fleets that survive return to origin.

## Data Model

### fleet

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| origin_planet_id | BIGINT FK → planet | |
| target_planet_id | BIGINT FK → planet | |
| player_id | BIGINT FK → player | |
| mission | VARCHAR(16) | ATTACK, DEPLOY |
| departure_time | TIMESTAMPTZ | |
| arrival_time | TIMESTAMPTZ | |
| return_time | TIMESTAMPTZ | When fleet arrives back at origin |
| status | VARCHAR(16) | EN_ROUTE, ARRIVED, RETURNING, RECALLED |
| metal_loot | DOUBLE | Resources captured from target |
| crystal_loot | DOUBLE | |
| gas_loot | DOUBLE | |

### fleet_ship

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| fleet_id | BIGINT FK → fleet | |
| ship_type | VARCHAR(32) | |
| quantity | INT | |

### combat_report

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| attacker_id | BIGINT | |
| defender_id | BIGINT | |
| attacker_planet_id | BIGINT | |
| defender_planet_id | BIGINT | |
| timestamp | TIMESTAMPTZ | |
| result | VARCHAR(16) | ATTACKER_WIN, DEFENDER_WIN, DRAW |
| attacker_ships_before | JSONB | Ship counts before combat |
| defender_ships_before | JSONB | |
| attacker_ships_lost | JSONB | |
| defender_ships_lost | JSONB | |
| debris_metal | DOUBLE | |
| debris_crystal | DOUBLE | |
| resources_looted | JSONB | Metal/crystal/gas captured |
| rounds | JSONB | Array of round data (shots fired) |

### debris_field

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| planet_id | BIGINT FK → planet | UNIQUE |
| metal | DOUBLE | |
| crystal | DOUBLE | |

## Ship Combat Stats

Values hardcoded in `GameBalancer`. Represent base values; multiplied by `game.speed` and technology effects later.

| Ship Type | Attack | Shield | Hull | Speed | Cargo |
|-----------|--------|--------|------|-------|-------|
| LIGHT_FIGHTER | 50 | 10 | 400 | 12500 | 50 |
| HEAVY_FIGHTER | 150 | 25 | 1000 | 10000 | 100 |
| CRUISER | 400 | 50 | 2700 | 15000 | 800 |
| BATTLESHIP | 1000 | 200 | 6000 | 10000 | 1500 |
| SMALL_CARGO | 5 | 10 | 400 | 5000 | 5000 |
| LARGE_CARGO | 5 | 25 | 1200 | 7500 | 25000 |
| COLONY_SHIP | 50 | 100 | 3000 | 2500 | 7500 |
| RECYCLER | 1 | 10 | 1600 | 2000 | 20000 |
| ESPIONAGE_PROBE | 0 | 0 | 100 | 10000000 | 0 |

## Rapid Fire Table

Keyed by firing ship type → target ship type → max extra shots. Implementation: after destroying a target, roll random(1..rf_value); if result > 1, fire again at another random target.

| Firer | Target | RF Value |
|-------|--------|----------|
| LIGHT_FIGHTER | ESPIONAGE_PROBE | 5 |
| HEAVY_FIGHTER | ESPIONAGE_PROBE | 5 |
| HEAVY_FIGHTER | SMALL_CARGO | 3 |
| CRUISER | ESPIONAGE_PROBE | 5 |
| CRUISER | LIGHT_FIGHTER | 3 |
| BATTLESHIP | ESPIONAGE_PROBE | 5 |
| BATTLESHIP | HEAVY_FIGHTER | 3 |
| BATTLESHIP | CRUISER | 2 |
| BATTLESHIP | SMALL_CARGO | 5 |

## Combat Resolution

Executed in GameLoop tick when fleet arrival_time ≤ now and status = EN_ROUTE.

1. Load defender's planet_ships (ships present at target planet)
2. Load attacker's fleet_ships (ships in the attacking fleet)
3. Generate lootable resources from target planet (capped by attacker's total cargo capacity)
4. Run combat rounds:
   a. Each round, every surviving ship picks a random enemy ship
   b. Damage = max(0, attack - shield). Applied to hull.
   c. If hull ≤ 0, ship is destroyed. 70% of metal + crystal added to debris.
   d. Roll rapid fire: if roll succeeds, ship fires again at another target
   e. Repeat until one side eliminated or max 6 rounds
5. After combat:
   a. If attacker wins: loot resources (capped by cargo), surviving ships return to origin
   b. If defender wins: defending ships survive, no loot
   c. If draw: surviving ships return to origin, no loot
6. Create/update debris field at target planet
7. Generate combat report with per-round data
8. Update planet_ship records (deduct losses)
9. Fleet status → RETURNING with return_time = now + travel time
10. WebSocket notification to both parties

## API Endpoints

All under `/api/game`:

| Method | Path | Purpose |
|--------|------|---------|
| POST | /planets/{planetId}/fleet | Launch fleet |
| GET | /planets/{planetId}/fleet | List outgoing fleets |
| GET | /fleet/{id} | Fleet detail |
| POST | /fleet/{id}/recall | Recall fleet |
| GET | /planets/{planetId}/combat-reports | Recent reports |
| GET | /combat-reports/{id} | Report detail |
| GET | /planets/{planetId}/debris | Debris field |

POST /planets/{planetId}/fleet body:
```json
{
  "mission": "ATTACK",
  "targetPlanetId": 42,
  "ships": { "LIGHT_FIGHTER": 10, "CRUISER": 3 }
}
```

Validates: player owns origin planet, target is different planet, ships exist at origin, enough fuel (ships have fuel requirements? — skip for now, assume unlimited), target exists.

## GameLoop Integration

New service `FleetService` handles fleet lifecycle. GameLoopService calls:

```
processFleetArrivals() → combat resolution / deploy execution
processFleetReturns() → transfer surviving ships back to origin
```

## Services

### FleetService
- `launchFleet(planetId, targetPlanetId, mission, ships, playerId)` — validate, deduct ships, create fleet record
- `getFleet(fleetId)` — detail
- `getPlanetFleets(planetId)` — outgoing fleets
- `recallFleet(fleetId, playerId)` — set status to RECALLED, set return_time
- `processArrivals(Instant now)` — game loop hook
- `processReturns(Instant now)` — game loop hook

### CombatService
- `resolveCombat(Fleet fleet, Planet targetPlanet)` — run combat, return CombatReport
- Debris field creation

## Frontend

### FleetComponent (/fleet)
- Show fleet at current planet (planet_ship display — already exists in ShipyardComponent)
- Launcher: select ships, quantity, target, mission type
- Active fleets: list with ETA, status bar
- Expandable detail: ships, position, ETA

### CombatReportComponent (modal or page)
- Summary: attacker, defender, result, resources looted, debris created
- Ships before/after comparison
- Per-round details (expandable)

### Debris display
- Show debris field on planet overview if present

## Implementation Plan Phasing

Phase 3a (this plan): Fleet system + Deploy mission + Attack combat + reports + debris
Phase 3b (future): Recycle mission (collect debris), Colonize, Spy, Transport

## Out of Scope
- Fuel consumption
- Fleet slow/calculate position on map
- Alliance combat
- Defensive buildings (plasma turrets, etc.)
- ACS (Attack Concertado) / joint attacks
- Moon creation from debris
- Espionage (probe only in Phase 3b)
