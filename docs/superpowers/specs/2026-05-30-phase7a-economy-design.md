# Phase 7a: Core Economy System

## Overview

Consolidate all resource management into a single `EconomyService`, add recurring production ticks via GameLoop, implement storage caps, fusion reactor (gas → energy), and energy deficit. Build frontend persistent resource header bar with production rate display.

## Approaches Considered

Three approaches were evaluated:
- **A: Big Bang** — everything (economy + UI + dark matter) in one phase. Rejected for risk.
- **B: Core → UI → Dark Matter (chosen)** — split into three sub-phases. Phase 7a delivers gameplay value fast.
- **C: Refactor-first** — EconomyService refactor without new behavior. Rejected/no visible value.

## Backend Architecture

### EconomyService (new)

Central resource authority — all resource operations go through here:

| Method | Purpose |
|---|---|
| `checkAndDeduct(planetId, metal, crystal, gas)` | Validate availability + atomic deduct |
| `refund(planetId, metal, crystal, gas)` | Return resources on queue cancellation |
| `addResources(planetId, metal, crystal, gas)` | Add external resources (loot, transport, trade) |
| `getCurrentResources(planetId)` | Return metal/crystal/gas/energy + caps + rates |
| `getStorageCaps(planet)` | Compute storage capacity from Building levels |
| `tickResources()` | Called by GameLoop every 10s — iterate all planets |

All existing services (BuildingService, ShipyardService, ResearchService, FleetService) are refactored to call `economyService.checkAndDeduct()` and `economyService.refund()` instead of inline resource logic.

### GameLoop Integration

- Every 10 seconds: `economyService.tickResources()`
- For each planet:
  1. Compute elapsed time since `planet.lastUpdated`
  2. Call `PlanetService.recalculate(planet)` to get current production rates (metal/hr, crystal/hr, gas/hr, net energy)
  3. Compute accrued: `rate * hoursElapsed`
  4. If netEnergy < 0: halve all mine production rates (already in GameBalancer)
  5. Deduct fusion reactor gas consumption from gas production
  6. Cap each resource to `min(resource + accrued, storageCap)`
  7. Update `planet.lastUpdated` and resource fields
  8. Batch save all planets

### Fusion Reactor

- `GameBalancer.getFusionEnergy(level)` = `30 * level * (1.05 + 0.01 * energyTechLevel)^level`
  - `energyTechLevel` from `PlayerTechnology` for ENERGY_TECHNOLOGY
- `GameBalancer.getFusionGasCost(level)` = `10 * level * 1.1^level`
- During tickResources():
  - If `planet.gas >= gasConsumption`: subtract gasConsumption, add fusionEnergy to net energy
  - Otherwise: reactor inactive, no energy produced, no gas consumed

### Energy Deficit

- In `PlanetService.recalculate()`:
  - `totalEnergy = solarPlantEnergy + fusionEnergy`
  - `totalConsumption = mineEnergyConsumption` (sum of all mine levels)
  - `netEnergy = totalEnergy - totalConsumption`
  - `isDeficit = netEnergy < 0`
- When `isDeficit == true`: mine production is halved (existing pattern in GameBalancer, now enforced)

### Storage Caps

- `GameBalancer.getStorageCapacity(level)` = `5000 * 2^(level-1)` (existing, now enforced)
- Applied in tickResources(): after adding accrued production, `min(amount, storageCap)`
- Applied in addResources(): after adding loot/transport, `min(amount, storageCap)`
- NOT applied in checkAndDeduct() — spending resources does not care about caps

## API Changes

### New Endpoint

```
GET /api/game/planets/{id}/resources
Response:
{
  "planetId": 1,
  "metal": 12450.0,
  "crystal": 8320.0,
  "gas": 5180.0,
  "energy": 42.0,
  "metalRate": 1200.0,
  "crystalRate": 800.0,
  "gasRate": 400.0,
  "metalStorage": 50000.0,
  "crystalStorage": 50000.0,
  "gasStorage": 50000.0,
  "energyConsumption": 158.0
}
```

### Modified Endpoints

- `GET /api/game/planets/{id}`: `resources` field now reflects EconomyService state (unchanged format)
- All build/ship/research/fleet endpoints: internally use EconomyService (no API change)

## Database

No new tables. Reuses existing Planet fields (`metal`, `crystal`, `gas`, `energy`, `last_updated`).

## Frontend Changes

### ResourceBarComponent (new)

- Standalone component displayed in AppComponent above `<router-outlet>`
- Polls `GET /planets/{activePlanetId}/resources` every 10 seconds
- Displays: Metal (blue), Crystal (teal), Gas (red), Energy (yellow) with amounts
- Shows storage cap ratio (e.g. "12,450 / 50,000")
- Color change near cap (>90%: orange background)
- Energy shown as `+42` or `-5` (red if negative)
- Tooltip on hover shows per-hour production rates
- Planet selector dropdown to switch active planet

### Active Planet State

- Shared via a `PlanetStateService` (BehaviorSubject<number>)
- AppComponent initializes from current route or first owned planet
- ResourceBarComponent subscribes and polls
- Navigation to planet-specific pages updates the active planet

### Component Changes

- **OverviewComponent**: Remove inline resource display block (now in header)
- **ResourcesComponent**: Remove inline header resource bar
- **AppComponent**: Add `<app-resource-bar>` template + import
- **Models**: Add `PlanetResourcesResponse` interface
- **GameService**: Add `getPlanetResources(planetId)` method

## Migration

No migration needed — all fields already exist on Planet table.

## Scope

Phase 7a = Core Economy only. Dark matter (earning, spending, quests) deferred to Phase 7b.

## Testing

- EconomyService unit tests: checkAndDeduct, refund, addResources, tickResources, storage caps
- Integration: GameLoop tick produces correct amounts over elapsed time
- E2E: resource bar shows correct values after build/ship cost deduction
- Fusion reactor: verify gas consumption and energy production balance
- Energy deficit: verify mine production halved when net energy < 0
