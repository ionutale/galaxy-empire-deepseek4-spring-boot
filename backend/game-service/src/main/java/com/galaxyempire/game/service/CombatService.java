package com.galaxyempire.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CombatService {

    private final PlanetShipRepository planetShipRepository;
    private final FleetShipRepository fleetShipRepository;
    private final DebrisFieldRepository debrisFieldRepository;
    private final CombatReportRepository combatReportRepository;
    private final PlanetRepository planetRepository;
    private final GameBalancer gameBalancer;
    private final ObjectMapper objectMapper;
    private final PlanetDefenseRepository planetDefenseRepository;

    public CombatService(PlanetShipRepository planetShipRepository,
                         FleetShipRepository fleetShipRepository,
                         DebrisFieldRepository debrisFieldRepository,
                         CombatReportRepository combatReportRepository,
                         PlanetRepository planetRepository,
                         GameBalancer gameBalancer,
                         ObjectMapper objectMapper,
                         PlanetDefenseRepository planetDefenseRepository) {
        this.planetShipRepository = planetShipRepository;
        this.fleetShipRepository = fleetShipRepository;
        this.debrisFieldRepository = debrisFieldRepository;
        this.combatReportRepository = combatReportRepository;
        this.planetRepository = planetRepository;
        this.gameBalancer = gameBalancer;
        this.objectMapper = objectMapper;
        this.planetDefenseRepository = planetDefenseRepository;
    }

    @Transactional
    public CombatReport resolveCombat(Fleet fleet, List<FleetShip> attackerShips) {
        Long targetPlanetId = fleet.getTargetPlanetId();
        List<PlanetShip> defenderShips = planetShipRepository.findByPlanetId(targetPlanetId);
        List<PlanetDefense> defenderDefenses = planetDefenseRepository.findByPlanetId(targetPlanetId);

        Map<String, Integer> attackerBefore = shipsToMap(attackerShips);
        Map<String, Integer> defenderBefore = shipsToMapFromPlanetShips(defenderShips);

        for (PlanetDefense pd : defenderDefenses) {
            if (pd.getQuantity() <= 0) continue;
            DefenseType dt = pd.getDefenseType();
            if (dt == DefenseType.SMALL_SHIELD || dt == DefenseType.LARGE_SHIELD) continue;
            for (int i = 0; i < pd.getQuantity(); i++) {
                ShipType target = pickRandomTarget(attackerShips, new Random());
                if (target == null) break;
                int shield = gameBalancer.getShipShield(target);
                int damage = Math.max(0, gameBalancer.getDefenseAttack(dt) - shield);
                if (damage > 0) {
                    int hull = gameBalancer.getShipHull(target);
                    int shipsDestroyed = Math.max(1, damage / hull);
                    for (FleetShip fs : attackerShips) {
                        if (fs.getShipType() == target && fs.getQuantity() > 0) {
                            int actual = Math.min(shipsDestroyed, fs.getQuantity());
                            fs.setQuantity(fs.getQuantity() - actual);
                            break;
                        }
                    }
                }
            }
        }

        List<FleetShip> attackerCurrent = deepCopyFleetShips(attackerShips);
        List<PlanetShip> defenderCurrent = new ArrayList<>(defenderShips);

        double debrisMetal = 0;
        double debrisCrystal = 0;
        List<Map<String, Object>> roundData = new ArrayList<>();

        int maxRounds = 6;

        for (int round = 0; round < maxRounds; round++) {
            if (attackerCurrent.isEmpty() || defenderCurrent.isEmpty()) break;

            Map<String, Integer> roundAttackerLosses = new HashMap<>();
            Map<String, Integer> roundDefenderLosses = new HashMap<>();

            for (FleetShip fs : new ArrayList<>(attackerCurrent)) {
                if (fs.getQuantity() <= 0) continue;
                fireShipGroup(fs.getShipType(), fs.getQuantity(), defenderCurrent,
                    roundDefenderLosses);
            }

            for (PlanetShip ps : new ArrayList<>(defenderCurrent)) {
                if (ps.getQuantity() <= 0) continue;
                fireShipGroup(ps.getShipType(), ps.getQuantity(), attackerCurrent, roundAttackerLosses);
            }

            removeZeroQuantity(attackerCurrent);
            removeZeroQuantity(defenderCurrent);

            Map<String, Object> roundEntry = new HashMap<>();
            roundEntry.put("round", round + 1);
            roundEntry.put("attackerLosses", roundAttackerLosses);
            roundEntry.put("defenderLosses", roundDefenderLosses);
            roundData.add(roundEntry);
        }

        Map<String, Integer> attackerLost = new HashMap<>();
        Map<String, Integer> defenderLost = new HashMap<>();
        for (Map<String, Object> rd : roundData) {
            mergeLosses(attackerLost, (Map<String, Integer>) rd.get("attackerLosses"));
            mergeLosses(defenderLost, (Map<String, Integer>) rd.get("defenderLosses"));
        }

        for (Map.Entry<String, Integer> entry : attackerLost.entrySet()) {
            ShipType type = ShipType.valueOf(entry.getKey());
            int count = entry.getValue();
            debrisMetal += gameBalancer.getShipMetalCost(type) * 0.3 * count;
            debrisCrystal += gameBalancer.getShipCrystalCost(type) * 0.3 * count;
        }
        for (Map.Entry<String, Integer> entry : defenderLost.entrySet()) {
            ShipType type = ShipType.valueOf(entry.getKey());
            int count = entry.getValue();
            debrisMetal += gameBalancer.getShipMetalCost(type) * 0.3 * count;
            debrisCrystal += gameBalancer.getShipCrystalCost(type) * 0.3 * count;
        }

        boolean attackerDefeated = attackerCurrent.isEmpty();
        boolean defenderDefeated = defenderCurrent.isEmpty();

        double survivalRate = attackerDefeated ? 1.0 : 0.5;
        for (PlanetDefense pd : defenderDefenses) {
            if (pd.getQuantity() > 0) {
                int destroyed = (int) Math.round(pd.getQuantity() * (1 - survivalRate));
                if (destroyed > 0) {
                    debrisMetal += gameBalancer.getDefenseMetalCost(pd.getDefenseType()) * 0.3 * destroyed;
                    debrisCrystal += gameBalancer.getDefenseCrystalCost(pd.getDefenseType()) * 0.3 * destroyed;
                    pd.setQuantity(pd.getQuantity() - destroyed);
                }
            }
        }

        String result;
        double lootedMetal = 0, lootedCrystal = 0, lootedGas = 0;

        if (defenderDefeated && !attackerDefeated) {
            result = "ATTACKER_WIN";
            Planet targetPlanet = planetRepository.findById(targetPlanetId).orElse(null);
            if (targetPlanet != null) {
                long totalCargo = attackerCurrent.stream()
                    .mapToLong(fs -> (long) gameBalancer.getShipCargo(fs.getShipType()) * fs.getQuantity())
                    .sum();
                lootedMetal = Math.min(targetPlanet.getMetal(), totalCargo / 3);
                lootedCrystal = Math.min(targetPlanet.getCrystal(), (totalCargo - lootedMetal) / 2);
                lootedGas = Math.min(targetPlanet.getGas(), totalCargo - lootedMetal - lootedCrystal);

                targetPlanet.setMetal(targetPlanet.getMetal() - lootedMetal);
                targetPlanet.setCrystal(targetPlanet.getCrystal() - lootedCrystal);
                targetPlanet.setGas(targetPlanet.getGas() - lootedGas);
                planetRepository.save(targetPlanet);
            }
        } else if (attackerDefeated && !defenderDefeated) {
            result = "DEFENDER_WIN";
        } else {
            result = "DRAW";
        }

        fleet.setMetalLoot(lootedMetal);
        fleet.setCrystalLoot(lootedCrystal);
        fleet.setGasLoot(lootedGas);

        Planet targetPlanet = planetRepository.findById(targetPlanetId).orElse(null);
        long defenderPlayerId = targetPlanet != null ? targetPlanet.getPlayerId() : 0L;

        planetShipRepository.deleteAll(defenderShips);
        planetShipRepository.saveAll(defenderCurrent);

        planetDefenseRepository.deleteAll(defenderDefenses);
        planetDefenseRepository.saveAll(defenderDefenses);

        List<FleetShip> existingShips = fleetShipRepository.findByFleetId(fleet.getId());
        fleetShipRepository.deleteAll(existingShips);
        for (FleetShip survivor : attackerCurrent) {
            if (survivor.getQuantity() > 0) {
                FleetShip toSave = new FleetShip(fleet.getId(), survivor.getShipType(), survivor.getQuantity());
                fleetShipRepository.save(toSave);
            }
        }

        if (debrisMetal > 0 || debrisCrystal > 0) {
            DebrisField df = debrisFieldRepository.findByPlanetId(targetPlanetId)
                .orElseGet(() -> debrisFieldRepository.save(new DebrisField(targetPlanetId)));
            df.addMetal(debrisMetal);
            df.addCrystal(debrisCrystal);
            debrisFieldRepository.save(df);
        }

        CombatReport report = new CombatReport();
        report.setAttackerId(fleet.getPlayerId());
        report.setDefenderId(defenderPlayerId);
        report.setAttackerPlanetId(fleet.getOriginPlanetId());
        report.setDefenderPlanetId(targetPlanetId);
        report.setTimestamp(Instant.now());
        report.setResult(result);
        report.setAttackerShipsBefore(toJson(attackerBefore));
        report.setDefenderShipsBefore(toJson(defenderBefore));
        report.setAttackerShipsLost(toJson(attackerLost));
        report.setDefenderShipsLost(toJson(defenderLost));
        report.setDebrisMetal(debrisMetal);
        report.setDebrisCrystal(debrisCrystal);
        report.setResourcesLooted(toJson(Map.of("metal", lootedMetal, "crystal", lootedCrystal, "gas", lootedGas)));
        report.setRounds(toJson(roundData));
        return combatReportRepository.save(report);
    }

    private void fireShipGroup(ShipType firerType, int quantity, List<?> targets,
                                 Map<String, Integer> losses) {
        Map<ShipType, Map<ShipType, Integer>> rapidFire = gameBalancer.getRapidFire();
        Map<ShipType, Integer> rfForFirer = rapidFire.getOrDefault(firerType, Map.of());
        int attack = gameBalancer.getShipAttack(firerType);
        Random rand = new Random();

        for (int i = 0; i < quantity; i++) {
            boolean canFire = true;
            while (canFire) {
                ShipType targetType = pickRandomTarget(targets, rand);
                if (targetType == null) { canFire = false; break; }

                int shield = gameBalancer.getShipShield(targetType);
                int hull = gameBalancer.getShipHull(targetType);
                int damage = Math.max(0, attack - shield);

                if (damage > 0) {
                    boolean destroyed = destroyShips(targets, targetType, damage, losses);
                    if (destroyed) {
                        int rfValue = rfForFirer.getOrDefault(targetType, 0);
                        if (rfValue > 0) {
                            int roll = rand.nextInt(rfValue) + 1;
                            canFire = roll > 1;
                        } else {
                            canFire = false;
                        }
                    } else {
                        canFire = false;
                    }
                } else {
                    canFire = false;
                }
            }
        }
    }

    private ShipType pickRandomTarget(List<?> targets, Random rand) {
        List<ShipType> available = new ArrayList<>();
        for (Object obj : targets) {
            if (obj instanceof FleetShip) {
                FleetShip fs = (FleetShip) obj;
                if (fs.getQuantity() > 0) available.add(fs.getShipType());
            } else if (obj instanceof PlanetShip) {
                PlanetShip ps = (PlanetShip) obj;
                if (ps.getQuantity() > 0) available.add(ps.getShipType());
            }
        }
        if (available.isEmpty()) return null;
        return available.get(rand.nextInt(available.size()));
    }

    private boolean destroyShips(List<?> targets, ShipType targetType, int damage,
                                  Map<String, Integer> losses) {
        int targetHull = gameBalancer.getShipHull(targetType);
        int shipsDestroyed = Math.max(1, damage / targetHull);

        for (Object obj : targets) {
            if (obj instanceof FleetShip) {
                FleetShip fs = (FleetShip) obj;
                if (fs.getShipType() == targetType && fs.getQuantity() > 0) {
                    int actual = Math.min(shipsDestroyed, fs.getQuantity());
                    fs.setQuantity(fs.getQuantity() - actual);
                    losses.merge(targetType.name(), actual, Integer::sum);
                    return true;
                }
            } else if (obj instanceof PlanetShip) {
                PlanetShip ps = (PlanetShip) obj;
                if (ps.getShipType() == targetType && ps.getQuantity() > 0) {
                    int actual = Math.min(shipsDestroyed, ps.getQuantity());
                    ps.setQuantity(ps.getQuantity() - actual);
                    losses.merge(targetType.name(), actual, Integer::sum);
                    return true;
                }
            }
        }
        return false;
    }

    private void removeZeroQuantity(List<?> list) {
        list.removeIf(obj -> {
            if (obj instanceof FleetShip) return ((FleetShip) obj).getQuantity() <= 0;
            if (obj instanceof PlanetShip) return ((PlanetShip) obj).getQuantity() <= 0;
            return false;
        });
    }

    private List<FleetShip> deepCopyFleetShips(List<FleetShip> original) {
        return original.stream()
            .map(fs -> new FleetShip(fs.getFleetId(), fs.getShipType(), fs.getQuantity()))
            .collect(Collectors.toList());
    }

    private Map<String, Integer> shipsToMap(List<FleetShip> ships) {
        Map<String, Integer> map = new HashMap<>();
        for (FleetShip fs : ships) {
            map.put(fs.getShipType().name(), fs.getQuantity());
        }
        return map;
    }

    private Map<String, Integer> shipsToMapFromPlanetShips(List<PlanetShip> ships) {
        Map<String, Integer> map = new HashMap<>();
        for (PlanetShip ps : ships) {
            map.put(ps.getShipType().name(), ps.getQuantity());
        }
        return map;
    }

    private void mergeLosses(Map<String, Integer> total, Map<String, Integer> round) {
        for (Map.Entry<String, Integer> e : round.entrySet()) {
            total.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
