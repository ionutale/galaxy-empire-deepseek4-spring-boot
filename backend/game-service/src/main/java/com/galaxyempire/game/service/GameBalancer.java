package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.BuildingType;
import com.galaxyempire.game.domain.DefenseType;
import com.galaxyempire.game.domain.ShipType;
import com.galaxyempire.game.domain.Technology;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class GameBalancer {

    private final double speed;

    public GameBalancer(@Value("${game.speed:1}") double speed) {
        this.speed = speed;
    }

    public double getMetalCost(BuildingType type, int level) {
        return switch (type) {
            case METAL_MINE -> Math.floor(60 * Math.pow(1.5, level - 1));
            case CRYSTAL_MINE -> Math.floor(48 * Math.pow(1.6, level - 1));
            case GAS_MINE -> Math.floor(225 * Math.pow(1.5, level - 1));
            case SOLAR_PLANT -> Math.floor(75 * Math.pow(1.5, level - 1));
            case METAL_STORAGE -> Math.floor(200 * Math.pow(2, level - 1));
            case CRYSTAL_STORAGE -> Math.floor(200 * Math.pow(2, level - 1));
            case GAS_STORAGE -> Math.floor(200 * Math.pow(2, level - 1));
            case ROBOT_FACTORY -> Math.floor(400 * Math.pow(2, level - 1));
            case RESEARCH_LAB -> Math.floor(200 * Math.pow(1.5, level - 1));
            case SHIPYARD -> Math.floor(400 * Math.pow(2, level - 1));
            default -> Math.floor(200 * Math.pow(1.5, level - 1));
        };
    }

    public double getCrystalCost(BuildingType type, int level) {
        return switch (type) {
            case METAL_MINE -> Math.floor(15 * Math.pow(1.5, level - 1));
            case CRYSTAL_MINE -> Math.floor(24 * Math.pow(1.6, level - 1));
            case GAS_MINE -> Math.floor(75 * Math.pow(1.5, level - 1));
            case SOLAR_PLANT -> Math.floor(30 * Math.pow(1.5, level - 1));
            case METAL_STORAGE -> Math.floor(200 * Math.pow(2, level - 1));
            case CRYSTAL_STORAGE -> Math.floor(200 * Math.pow(2, level - 1));
            case GAS_STORAGE -> Math.floor(200 * Math.pow(2, level - 1));
            case ROBOT_FACTORY -> Math.floor(200 * Math.pow(2, level - 1));
            case RESEARCH_LAB -> Math.floor(400 * Math.pow(1.5, level - 1));
            case SHIPYARD -> Math.floor(200 * Math.pow(2, level - 1));
            default -> Math.floor(200 * Math.pow(1.5, level - 1));
        };
    }

    public double getGasCost(BuildingType type, int level) {
        return switch (type) {
            case METAL_MINE, CRYSTAL_MINE, GAS_MINE -> 0;
            case SOLAR_PLANT -> Math.floor(15 * Math.pow(1.5, level - 1));
            case RESEARCH_LAB -> Math.floor(200 * Math.pow(1.5, level - 1));
            default -> 0;
        };
    }

    public int getConstructionTimeSeconds(BuildingType type, int level, int robotFactoryLevel) {
        double baseTime = switch (type) {
            case METAL_MINE -> 3600;
            case CRYSTAL_MINE -> 4800;
            case GAS_MINE -> 6000;
            case SOLAR_PLANT -> 2400;
            case METAL_STORAGE, CRYSTAL_STORAGE, GAS_STORAGE -> 1200;
            case ROBOT_FACTORY -> 4800;
            case RESEARCH_LAB -> 7200;
            case SHIPYARD -> 9600;
            default -> 3600;
        };
        double time = baseTime * Math.pow(1.5, level - 1) / (1 + robotFactoryLevel);
        return (int) Math.max(Math.round(time / speed), 10);
    }

    public double getMetalProductionPerHour(int level, int temperature, boolean energyPositive) {
        double base = 30 * level * Math.pow(1.1, level);
        if (!energyPositive) base *= 0.5;
        return base * speed;
    }

    public double getCrystalProductionPerHour(int level, int temperature, boolean energyPositive) {
        double base = 20 * level * Math.pow(1.1, level);
        if (!energyPositive) base *= 0.5;
        return base * speed;
    }

    public double getGasProductionPerHour(int level, int temperature, boolean energyPositive) {
        double tempFactor = 1.0 + (100 - temperature) / 200.0;
        double base = 10 * level * Math.pow(1.1, level) * tempFactor;
        if (!energyPositive) base *= 0.5;
        return base * speed;
    }

    public double getSolarPlantEnergy(int level) {
        return 20 * level * Math.pow(1.1, level);
    }

    public double getFusionEnergy(int level, int energyTechLevel) {
        double baseMultiplier = 1.05 + 0.01 * energyTechLevel;
        return 30 * level * Math.pow(baseMultiplier, level);
    }

    public double getFusionGasCost(int level) {
        return 10 * level * Math.pow(1.1, level);
    }

    public double getMineEnergyConsumption(int level) {
        return 10 * level * Math.pow(1.1, level);
    }

    public double getMineEnergyConsumption(BuildingType type, int level) {
        return switch (type) {
            case METAL_MINE, CRYSTAL_MINE, GAS_MINE -> getMineEnergyConsumption(level);
            default -> 0;
        };
    }

    public double getStorageCapacity(int level) {
        return 5000 * Math.pow(2, level - 1);
    }

    public double getTechnologyMetalCost(Technology tech, int level) {
        long base = switch (tech) {
            case ENERGY_TECH -> 200;
            case LASER_TECH -> 100;
            case ION_TECH -> 250;
            case PLASMA_TECH -> 500;
            case COMBUSTION_DRIVE -> 200;
            case IMPULSE_DRIVE -> 1000;
            case HYPERSPACE_DRIVE -> 2000;
            case WEAPON_TECH -> 400;
            case SHIELDING_TECH -> 200;
            case ARMOR_TECH -> 200;
            case COMPUTER_TECH -> 100;
            case ESPIONAGE_TECH -> 200;
            case GRAVITON_TECH -> 5000;
        };
        return Math.floor(base * Math.pow(2, level)) * speed;
    }

    public double getTechnologyCrystalCost(Technology tech, int level) {
        long base = switch (tech) {
            case ENERGY_TECH -> 100;
            case LASER_TECH -> 50;
            case ION_TECH -> 150;
            case PLASMA_TECH -> 300;
            case COMBUSTION_DRIVE -> 100;
            case IMPULSE_DRIVE -> 500;
            case HYPERSPACE_DRIVE -> 1000;
            case WEAPON_TECH -> 200;
            case SHIELDING_TECH -> 400;
            case ARMOR_TECH -> 100;
            case COMPUTER_TECH -> 200;
            case ESPIONAGE_TECH -> 400;
            case GRAVITON_TECH -> 5000;
        };
        return Math.floor(base * Math.pow(2, level)) * speed;
    }

    public double getTechnologyGasCost(Technology tech, int level) {
        long base = switch (tech) {
            case PLASMA_TECH, IMPULSE_DRIVE, ESPIONAGE_TECH -> 100;
            case HYPERSPACE_DRIVE -> 500;
            case GRAVITON_TECH -> 1000;
            default -> 0;
        };
        if (base == 0) return 0;
        return Math.floor(base * Math.pow(2, level)) * speed;
    }

    public int getResearchTimeSeconds(Technology tech, int level, double researchLabLevel) {
        long base = switch (tech) {
            case ENERGY_TECH -> 600;
            case LASER_TECH -> 400;
            case ION_TECH -> 800;
            case PLASMA_TECH -> 2000;
            case COMBUSTION_DRIVE -> 600;
            case IMPULSE_DRIVE -> 1800;
            case HYPERSPACE_DRIVE -> 3600;
            case WEAPON_TECH -> 1200;
            case SHIELDING_TECH -> 1200;
            case ARMOR_TECH -> 600;
            case COMPUTER_TECH -> 400;
            case ESPIONAGE_TECH -> 1200;
            case GRAVITON_TECH -> 14400;
        };
        double time = base * Math.pow(2, level) / (1 + researchLabLevel);
        return (int) Math.ceil(time / speed);
    }

    public boolean meetsPrerequisites(Technology tech, Map<Technology, Integer> playerTechLevels) {
        return switch (tech) {
            case PLASMA_TECH ->
                playerTechLevels.getOrDefault(Technology.ENERGY_TECH, 0) >= 5
                && playerTechLevels.getOrDefault(Technology.LASER_TECH, 0) >= 5;
            case COMBUSTION_DRIVE ->
                playerTechLevels.getOrDefault(Technology.ENERGY_TECH, 0) >= 1;
            case IMPULSE_DRIVE ->
                playerTechLevels.getOrDefault(Technology.COMBUSTION_DRIVE, 0) >= 5
                && playerTechLevels.getOrDefault(Technology.ENERGY_TECH, 0) >= 2;
            case HYPERSPACE_DRIVE ->
                playerTechLevels.getOrDefault(Technology.IMPULSE_DRIVE, 0) >= 5
                && playerTechLevels.getOrDefault(Technology.ENERGY_TECH, 0) >= 3;
            case WEAPON_TECH ->
                playerTechLevels.getOrDefault(Technology.LASER_TECH, 0) >= 3;
            case SHIELDING_TECH ->
                playerTechLevels.getOrDefault(Technology.ENERGY_TECH, 0) >= 3;
            case ESPIONAGE_TECH ->
                playerTechLevels.getOrDefault(Technology.COMPUTER_TECH, 0) >= 3;
            case GRAVITON_TECH ->
                playerTechLevels.getOrDefault(Technology.ENERGY_TECH, 0) >= 10
                && playerTechLevels.getOrDefault(Technology.PLASMA_TECH, 0) >= 5;
            default -> true;
        };
    }

    public double getTechnologyEffect(Technology tech, int level) {
        return 1.0 + 0.05 * level;
    }

    public double getShipMetalCost(ShipType type) {
        return switch (type) {
            case LIGHT_FIGHTER -> 500;
            case HEAVY_FIGHTER -> 2500;
            case CRUISER -> 5000;
            case BATTLESHIP -> 15000;
            case SMALL_CARGO -> 1000;
            case LARGE_CARGO -> 3000;
            case COLONY_SHIP -> 5000;
            case RECYCLER -> 2000;
            case ESPIONAGE_PROBE -> 100;
        } * speed;
    }

    public double getShipCrystalCost(ShipType type) {
        return switch (type) {
            case LIGHT_FIGHTER -> 100;
            case HEAVY_FIGHTER -> 500;
            case CRUISER -> 2000;
            case BATTLESHIP -> 5000;
            case SMALL_CARGO -> 500;
            case LARGE_CARGO -> 1500;
            case COLONY_SHIP -> 2500;
            case RECYCLER -> 1000;
            case ESPIONAGE_PROBE -> 50;
        } * speed;
    }

    public double getShipGasCost(ShipType type) {
        return switch (type) {
            case CRUISER -> 1000;
            case BATTLESHIP -> 3000;
            case COLONY_SHIP -> 5000;
            case RECYCLER -> 500;
            default -> 0;
        } * speed;
    }

    public int getShipBuildTimeSeconds(ShipType type, double shipyardLevel, double naniteLevel) {
        int base = switch (type) {
            case LIGHT_FIGHTER -> 120;
            case HEAVY_FIGHTER -> 360;
            case CRUISER -> 1200;
            case BATTLESHIP -> 3600;
            case SMALL_CARGO -> 240;
            case LARGE_CARGO -> 600;
            case COLONY_SHIP -> 2400;
            case RECYCLER -> 600;
            case ESPIONAGE_PROBE -> 30;
        };
        return (int) Math.ceil(base * speed / (1 + shipyardLevel + naniteLevel));
    }

    public int getRequiredShipyardLevel(ShipType type) {
        return switch (type) {
            case LIGHT_FIGHTER, ESPIONAGE_PROBE -> 1;
            case SMALL_CARGO -> 2;
            case RECYCLER, HEAVY_FIGHTER -> 3;
            case LARGE_CARGO -> 4;
            case CRUISER, COLONY_SHIP -> 5;
            case BATTLESHIP -> 7;
        };
    }

    public int getShipAttack(ShipType type) {
        return switch (type) {
            case LIGHT_FIGHTER -> 50;
            case HEAVY_FIGHTER -> 150;
            case CRUISER -> 400;
            case BATTLESHIP -> 1000;
            case SMALL_CARGO -> 5;
            case LARGE_CARGO -> 5;
            case COLONY_SHIP -> 50;
            case RECYCLER -> 1;
            case ESPIONAGE_PROBE -> 0;
        };
    }

    public int getShipShield(ShipType type) {
        return switch (type) {
            case LIGHT_FIGHTER -> 10;
            case HEAVY_FIGHTER -> 25;
            case CRUISER -> 50;
            case BATTLESHIP -> 200;
            case SMALL_CARGO -> 10;
            case LARGE_CARGO -> 25;
            case COLONY_SHIP -> 100;
            case RECYCLER -> 10;
            case ESPIONAGE_PROBE -> 0;
        };
    }

    public int getShipHull(ShipType type) {
        return switch (type) {
            case LIGHT_FIGHTER -> 400;
            case HEAVY_FIGHTER -> 1000;
            case CRUISER -> 2700;
            case BATTLESHIP -> 6000;
            case SMALL_CARGO -> 400;
            case LARGE_CARGO -> 1200;
            case COLONY_SHIP -> 3000;
            case RECYCLER -> 1600;
            case ESPIONAGE_PROBE -> 100;
        };
    }

    public int getShipSpeed(ShipType type) {
        return switch (type) {
            case LIGHT_FIGHTER -> 12500;
            case HEAVY_FIGHTER -> 10000;
            case CRUISER -> 15000;
            case BATTLESHIP -> 10000;
            case SMALL_CARGO -> 5000;
            case LARGE_CARGO -> 7500;
            case COLONY_SHIP -> 2500;
            case RECYCLER -> 2000;
            case ESPIONAGE_PROBE -> 10000000;
        };
    }

    public int getShipCargo(ShipType type) {
        return switch (type) {
            case LIGHT_FIGHTER -> 50;
            case HEAVY_FIGHTER -> 100;
            case CRUISER -> 800;
            case BATTLESHIP -> 1500;
            case SMALL_CARGO -> 5000;
            case LARGE_CARGO -> 25000;
            case COLONY_SHIP -> 7500;
            case RECYCLER -> 20000;
            case ESPIONAGE_PROBE -> 0;
        };
    }

    public Map<ShipType, Map<ShipType, Integer>> getRapidFire() {
        Map<ShipType, Map<ShipType, Integer>> rf = new HashMap<>();
        rf.put(ShipType.LIGHT_FIGHTER, Map.of(ShipType.ESPIONAGE_PROBE, 5));
        rf.put(ShipType.HEAVY_FIGHTER, Map.of(ShipType.ESPIONAGE_PROBE, 5, ShipType.SMALL_CARGO, 3));
        rf.put(ShipType.CRUISER, Map.of(ShipType.ESPIONAGE_PROBE, 5, ShipType.LIGHT_FIGHTER, 3));
        rf.put(ShipType.BATTLESHIP, Map.of(ShipType.ESPIONAGE_PROBE, 5, ShipType.HEAVY_FIGHTER, 3, ShipType.CRUISER, 2, ShipType.SMALL_CARGO, 5));
        return rf;
    }

    public int getTravelTimeSeconds(long distance) {
        return Math.max(10, (int) (distance / speed));
    }

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
}
