package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.BuildingType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    public double getMineEnergyConsumption(BuildingType type, int level) {
        return switch (type) {
            case METAL_MINE -> 10 * level * Math.pow(1.1, level);
            case CRYSTAL_MINE -> 10 * level * Math.pow(1.1, level);
            case GAS_MINE -> 10 * level * Math.pow(1.1, level);
            default -> 0;
        };
    }

    public double getStorageCapacity(int level) {
        return 5000 * Math.pow(2, level - 1);
    }
}
