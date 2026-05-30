package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.BuildingType;
import com.galaxyempire.game.domain.DefenseType;
import com.galaxyempire.game.domain.ShipType;
import com.galaxyempire.game.domain.Technology;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pure-formula coverage for {@link GameBalancer}. Constructed with speed = 1.0
 * so the expected values below are the unscaled base formulas.
 */
class GameBalancerTest {

    private final GameBalancer balancer = new GameBalancer(1.0);

    // --- Building costs ---

    @Test
    void metalCostGrowsGeometricallyWithLevel() {
        assertThat(balancer.getMetalCost(BuildingType.METAL_MINE, 1)).isEqualTo(60.0);
        assertThat(balancer.getMetalCost(BuildingType.METAL_MINE, 2)).isEqualTo(90.0);   // floor(60 * 1.5)
        assertThat(balancer.getMetalCost(BuildingType.METAL_MINE, 3)).isEqualTo(135.0);  // floor(60 * 1.5^2)
    }

    @Test
    void crystalCostAtLevelOneIsBaseValue() {
        assertThat(balancer.getCrystalCost(BuildingType.METAL_MINE, 1)).isEqualTo(15.0);
        assertThat(balancer.getCrystalCost(BuildingType.CRYSTAL_MINE, 1)).isEqualTo(24.0);
    }

    @Test
    void minesHaveNoGasCost() {
        assertThat(balancer.getGasCost(BuildingType.METAL_MINE, 5)).isZero();
        assertThat(balancer.getGasCost(BuildingType.CRYSTAL_MINE, 5)).isZero();
        assertThat(balancer.getGasCost(BuildingType.GAS_MINE, 5)).isZero();
    }

    // --- Construction time ---

    @Test
    void robotFactoryReducesConstructionTime() {
        int noFactory = balancer.getConstructionTimeSeconds(BuildingType.METAL_MINE, 1, 0);
        int withFactory = balancer.getConstructionTimeSeconds(BuildingType.METAL_MINE, 1, 4);
        assertThat(noFactory).isEqualTo(3600);
        assertThat(withFactory).isEqualTo(720); // 3600 / (1 + 4)
    }

    @Test
    void constructionTimeNeverDropsBelowFloor() {
        int t = balancer.getConstructionTimeSeconds(BuildingType.METAL_STORAGE, 1, 1000);
        assertThat(t).isGreaterThanOrEqualTo(10);
    }

    // --- Production & energy ---

    @Test
    void energyDeficitHalvesMetalProduction() {
        double positive = balancer.getMetalProductionPerHour(1, 50, true);
        double deficit = balancer.getMetalProductionPerHour(1, 50, false);
        assertThat(positive).isEqualTo(33.0, within(0.0001)); // 30 * 1 * 1.1
        assertThat(deficit).isEqualTo(positive * 0.5, within(0.0001));
    }

    @Test
    void storageCapacityDoublesPerLevel() {
        assertThat(balancer.getStorageCapacity(1)).isEqualTo(5000.0);
        assertThat(balancer.getStorageCapacity(2)).isEqualTo(10000.0);
        assertThat(balancer.getStorageCapacity(3)).isEqualTo(20000.0);
    }

    @Test
    void fusionEnergyScalesWithEnergyTech() {
        double low = balancer.getFusionEnergy(5, 0);
        double high = balancer.getFusionEnergy(5, 10);
        assertThat(high).isGreaterThan(low);
    }

    // --- Technology ---

    @Test
    void technologyCostDoublesPerLevel() {
        assertThat(balancer.getTechnologyMetalCost(Technology.ENERGY_TECH, 1)).isEqualTo(400.0);  // 200 * 2^1
        assertThat(balancer.getTechnologyMetalCost(Technology.ENERGY_TECH, 2)).isEqualTo(800.0);  // 200 * 2^2
    }

    @Test
    void plasmaTechRequiresEnergyAndLaserAtLevelFive() {
        assertThat(balancer.meetsPrerequisites(Technology.PLASMA_TECH,
                Map.of(Technology.ENERGY_TECH, 5, Technology.LASER_TECH, 5))).isTrue();
        assertThat(balancer.meetsPrerequisites(Technology.PLASMA_TECH,
                Map.of(Technology.ENERGY_TECH, 5, Technology.LASER_TECH, 4))).isFalse();
        assertThat(balancer.meetsPrerequisites(Technology.PLASMA_TECH, Map.of())).isFalse();
    }

    @Test
    void baseTechnologiesHaveNoPrerequisites() {
        assertThat(balancer.meetsPrerequisites(Technology.ENERGY_TECH, Map.of())).isTrue();
        assertThat(balancer.meetsPrerequisites(Technology.LASER_TECH, Map.of())).isTrue();
    }

    // --- Ships ---

    @Test
    void shipCostsMatchSpecForLightFighter() {
        assertThat(balancer.getShipMetalCost(ShipType.LIGHT_FIGHTER)).isEqualTo(500.0);
        assertThat(balancer.getShipCrystalCost(ShipType.LIGHT_FIGHTER)).isEqualTo(100.0);
        assertThat(balancer.getShipGasCost(ShipType.LIGHT_FIGHTER)).isZero();
    }

    @Test
    void battleshipOutgunsLightFighter() {
        assertThat(balancer.getShipAttack(ShipType.BATTLESHIP))
                .isGreaterThan(balancer.getShipAttack(ShipType.LIGHT_FIGHTER));
        assertThat(balancer.getShipHull(ShipType.BATTLESHIP))
                .isGreaterThan(balancer.getShipHull(ShipType.LIGHT_FIGHTER));
    }

    @Test
    void espionageProbeHasNoCargoOrAttack() {
        assertThat(balancer.getShipCargo(ShipType.ESPIONAGE_PROBE)).isZero();
        assertThat(balancer.getShipAttack(ShipType.ESPIONAGE_PROBE)).isZero();
    }

    @Test
    void shipyardRequirementsAreOrdered() {
        assertThat(balancer.getRequiredShipyardLevel(ShipType.LIGHT_FIGHTER)).isEqualTo(1);
        assertThat(balancer.getRequiredShipyardLevel(ShipType.BATTLESHIP)).isEqualTo(7);
    }

    // --- Travel time ---

    @Test
    void travelTimeHasMinimumOfTenSeconds() {
        assertThat(balancer.getTravelTimeSeconds(5)).isEqualTo(10);
        assertThat(balancer.getTravelTimeSeconds(5000)).isEqualTo(5000);
    }

    // --- Defenses ---

    @Test
    void plasmaTurretIsStrongestDefense() {
        assertThat(balancer.getDefenseAttack(DefenseType.PLASMA_TURRET))
                .isGreaterThan(balancer.getDefenseAttack(DefenseType.ROCKET_LAUNCHER));
    }

    @Test
    void rocketLauncherCostsOnlyMetal() {
        assertThat(balancer.getDefenseMetalCost(DefenseType.ROCKET_LAUNCHER)).isEqualTo(2000.0);
        assertThat(balancer.getDefenseCrystalCost(DefenseType.ROCKET_LAUNCHER)).isZero();
        assertThat(balancer.getDefenseGasCost(DefenseType.ROCKET_LAUNCHER)).isZero();
    }

    // --- Speed scaling ---

    @Test
    void higherGameSpeedReducesConstructionTime() {
        GameBalancer fast = new GameBalancer(2.0);
        int normal = balancer.getConstructionTimeSeconds(BuildingType.RESEARCH_LAB, 3, 0);
        int quick = fast.getConstructionTimeSeconds(BuildingType.RESEARCH_LAB, 3, 0);
        assertThat(quick).isLessThan(normal);
    }
}
