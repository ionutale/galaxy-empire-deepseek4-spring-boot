# Phase 5: Planetary Defenses

## Design

Defenses are stationary combat units built in the Shipyard and deployed on planets. They participate in combat when the planet is attacked, firing before ships. Built via ShipyardQueue (extended with `defense_type`) and stored in a new `planet_defense` table.

### Defense Types

| Type | Attack | Shield | Hull | Metal | Crystal | Gas | Build Time | Shipyard Req |
|------|--------|--------|------|-------|---------|-----|------------|--------------|
| ROCKET_LAUNCHER | 80 | 20 | 200 | 2,000 | 0 | 0 | 300s | 1 |
| LIGHT_LASER | 100 | 25 | 200 | 1,500 | 500 | 0 | 240s | 2 |
| HEAVY_LASER | 250 | 100 | 800 | 6,000 | 2,000 | 0 | 600s | 4 |
| ION_CANNON | 150 | 500 | 800 | 2,000 | 6,000 | 0 | 1200s | 5 |
| PLASMA_TURRET | 3,000 | 300 | 2,000 | 50,000 | 50,000 | 30,000 | 7200s | 8 |
| SMALL_SHIELD | 1 | 2,000 | 2,000 | 10,000 | 10,000 | 0 | 1200s | 3 |
| LARGE_SHIELD | 1 | 10,000 | 10,000 | 50,000 | 50,000 | 0 | 7200s | 6 |

### Combat Integration

- On ATTACK arrival: load `PlanetDefense` list for target planet
- Defenses fire **before** ships each round, targeting random attacker ships (same rapid fire mechanic)
- Attacker ships fire at defenses too (attacker's ships can target defenses)
- Destroyed defenses produce debris at 30% of resource cost
- Shield Domes: Small/Large Shield act as additional HP pool — all damage goes to shield first until depleted
- After combat: surviving defenses stay on planet (unlike ships which are all removed and replaced)

### Architecture

New files: `DefenseType.java`, `PlanetDefense.java`, `PlanetDefenseRepository.java` + V8 migration

Modified files: `ShipyardQueue.java` (add nullable `defense_type`), `ShipyardService.java` (build defenses), `CombatService.java` (resolve defenses), `GameBalancer.java` (defense stats), `GameLoopService.java` (defense building completion), frontend `ShipyardComponent` (defense tab)
