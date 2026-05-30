# Phase 4: Remaining Fleet Missions (Transport, Colonize, Spy, Recycle)

## Overview

Extend the Phase 3 fleet system with the 4 remaining mission types. All build on the existing `FleetService` lifecycle (launch → arrival → return) and reuse the same `Fleet`, `FleetShip`, and `FleetController` infrastructure.

## 1. FleetMission Enum

Add to `FleetMission.java`:

```java
TRANSPORT, COLONIZE, SPY, RECYCLE
```

**V7 migration** alters the enum column's underlying type (or uses a string comparison — already `EnumType.STRING` so no DB migration needed for the enum itself beyond inserting rows that reference new values).

## 2. Transport

**Launch validation** (in `FleetService.launchFleet`):
- Target must be owned by the current player (`target.playerId == playerId`)
- Request body includes `resources` object with `metal`, `crystal`, `gas` amounts
- Total cargo required = sum of resource amounts. Ships' combined cargo capacity (`getShipCargo` × quantity) must ≥ that total
- Origin planet must have enough resources to send
- Deduct resources from origin planet on launch
- Enqueue fleet with `metalLoot`, `crystalLoot`, `gasLoot` set to the specified amounts

**Arrival** (in `FleetService.processArrivals`):
- Transfer `fleet.metalLoot/crystalLoot/gasLoot` into the target planet's resources
- Fleet status → `RETURNING`, return time set (same logic as ATTACK survivors)

**Return** (existing `processReturns`):
- No resources to deposit on return (already transferred on arrival). Ships return empty.

## 3. Colonize

**Launch validation**:
- Fleet must contain at least 1 `COLONY_SHIP`
- Request body includes `galaxy`, `systemId`, `slot` instead of `targetPlanetId`
- Validate all 3 coordinates are in range (1-9, 1-500, 1-15)
- Validate no planet exists at those coordinates
- Create the Planet immediately at those coordinates for the player, with starter buildings (same as `createStarterPlanet`)
- Use the new Planet's ID as `targetPlanetId` on the Fleet (consistent with all other missions)

**Arrival**:
- Planet already exists (created at launch). Mark it as active/colonized (no additional action needed)
- Deduct 1 COLONY_SHIP from the fleet (the ship is consumed establishing the colony)
- Fleet status → `RETURNING` (remaining ships return)

## 4. Spy (Probe)

**Launch validation**:
- Fleet must contain at least 1 `ESPIONAGE_PROBE`
- Target planet must belong to a different player (`target.playerId != playerId`)

**Arrival** (espionage resolution):
- Compare attacker's `ESPIONAGE_TECH` level vs defender's `ESPIONAGE_TECH` level
- If attacker level > defender level: probes survive, generate `EspionageReport`
- If attacker level ≤ defender level: all probes destroyed (removed from fleet), no report
- Fleet status → `RETURNING` (empty if probes were destroyed)

**EspionageReport entity**:
| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| attacker_id | BIGINT NOT NULL | |
| defender_id | BIGINT NOT NULL | |
| target_planet_id | BIGINT NOT NULL | |
| timestamp | TIMESTAMPTZ NOT NULL | |
| resources_json | JSONB | `{ "metal": ..., "crystal": ..., "gas": ... }` |
| ships_json | JSONB | `{ "LIGHT_FIGHTER": 5, ... }` |
| buildings_json | JSONB | `{ "METAL_MINE": 3, ... }` |
| technologies_json | JSONB | `{ "ENERGY_TECH": 2, ... }` |
| defenses_json | JSONB | `{}` (reserved for Phase 5) |

Content shown is based on `espionage_tech_level - target_espionage_tech_level`:
- Equal: only resources
- +1: + ships
- +2: + buildings
- +3: + technologies
- +4: + defenses (future)
- +6+: exact counts (normally rounded down)

## 5. Recycle

**Launch validation**:
- Fleet must contain at least 1 `RECYCLER`
- Target planet must have a `DebrisField` with `metal > 0` or `crystal > 0`

**Arrival**:
- Calculate total Recycler cargo capacity (`getShipCargo(RECYCLER)` = 20,000 × number of Recyclers)
- Transfer debris into `fleet.metalLoot` and `fleet.crystalLoot`, capped at total cargo capacity
- Reduce the debris field by the collected amount
- If all debris collected, remove the debris field
- Fleet status → `RETURNING`

**Return** (existing `processReturns`):
- Deposit `fleet.metalLoot` and `fleet.crystalLoot` into origin planet (already handled by the generic loot deposit logic)

## 6. Backend Changes Summary

| File | Change |
|------|--------|
| `FleetMission.java` | Add 4 new enum values |
| `FleetService.java` | Extend `launchFleet` validation per mission; extend `processArrivals` per mission |
| `PlanetService.java` | Add `createPlanetAt(playerId, galaxy, systemId, slot)` |
| `EspionageReport.java` | New entity (in domain package) |
| `EspionageReportRepository.java` | New repository |
| `FleetController.java` | Accept `galaxy`/`systemId`/`slot` for COLONIZE (instead of `targetPlanetId`); add `GET /api/game/planets/{planetId}/espionage-reports`; accept `resources` for TRANSPORT |
| `V7__add_remaining_missions.sql` | Create espionage_report table |

## 7. Frontend Changes

The existing `FleetComponent` is extended with mission-specific UI sections:

- **Mission selector** now has 5 options: Attack, Deploy, Transport, Colonize, Spy, Recycle
- **Transport**: When selected, show resource amount inputs (metal, crystal, gas) + cargo capacity summary (total capacity of selected ships)
- **Colonize**: When selected, show coordinate inputs (galaxy 1-9, system 1-500, slot 1-15) + require at least 1 Colony Ship selected
- **Spy**: Same target input as Attack, but require Espionage Probe selected
- **Recycle**: Show current debris field at target planet, require Recycler selected
- **Espionage Reports**: New section below Active Fleets showing past spy reports
- **API methods** added to `game.service.ts`: `getEspionageReports(planetId)` (or similar)

## 8. Data Flow

```
Frontend                    Backend (FleetController)     FleetService          DB
-------                     ---------------------------   -----------           --
Launch form (mission=X)  →  POST /fleet                    launchFleet() →      INSERT fleet, fleet_ship
                            (validates per mission type)                        Decrement planet_ship
                                                                                  
                            GameLoop (every 5s)            processArrivals() →  UPDATE fleet status
                                                                                 EspionageReport (SPY)
                                                                                 Planet resources (TRANSPORT)
                                                                                 New Planet (COLONIZE)
                                                                                 DebrisField (RECYCLE)
                                                                                 5s later:
                                                           processReturns() →   Ships back to origin
                                                                                 Loot deposited (RECYCLE)
```

## 9. Scope & Decomposition

All 4 missions fit in one phase. Estimated tasks:

1. V7 migration (espionage_report table)
2. FleetMission enum update
3. Transport: launch validation + arrival logic in FleetService
4. Colonize: launch validation + PlanetService.createPlanetAt + arrival logic
5. Spy: EspionageReport entity + repo + arrival logic (espionage comparison)
6. Recycle: launch validation + arrival logic (debris collection)
7. EspionageReportRepository + FleetController endpoint
8. Frontend: extended FleetComponent mission UI + espionage report display
9. Verify build + tests
