package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.Building;
import com.galaxyempire.game.domain.BuildingType;
import com.galaxyempire.game.domain.Planet;
import com.galaxyempire.game.repository.BuildingRepository;
import com.galaxyempire.game.repository.PlanetRepository;
import com.galaxyempire.game.repository.PlayerTechnologyRepository;
import com.galaxyempire.game.domain.Technology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EconomyServiceTest {

    @Mock private PlanetRepository planetRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private PlayerTechnologyRepository playerTechnologyRepository;

    private final GameBalancer balancer = new GameBalancer(1.0);
    private EconomyService service;

    private Planet planet;

    @BeforeEach
    void setUp() {
        service = new EconomyService(planetRepository, buildingRepository, balancer, playerTechnologyRepository);
        planet = new Planet();
        planet.setId(1L);
        planet.setPlayerId(1L);
        planet.setTemperature(50);
        lenient().when(planetRepository.findById(1L)).thenReturn(Optional.of(planet));
        lenient().when(planetRepository.save(any(Planet.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void addResourcesAccumulatesBelowStorageCap() {
        when(buildingRepository.findByPlanetId(1L)).thenReturn(List.of());
        planet.setMetal(100);

        service.addResources(1L, 50, 0, 0);

        assertThat(planet.getMetal()).isEqualTo(150);
    }

    @Test
    void addResourcesClampsAtStorageCapacity() {
        // No storage buildings -> capacity = getStorageCapacity(0) = 2500
        when(buildingRepository.findByPlanetId(1L)).thenReturn(List.of());
        planet.setMetal(2400);

        service.addResources(1L, 1000, 0, 0);

        assertThat(planet.getMetal()).isEqualTo(2500.0);
    }

    @Test
    void checkAndDeductFailsWhenInsufficientFunds() {
        planet.setMetal(50);
        boolean ok = service.checkAndDeduct(1L, 100, 0, 0);
        assertThat(ok).isFalse();
        assertThat(planet.getMetal()).isEqualTo(50); // unchanged
    }

    @Test
    void checkAndDeductSubtractsWhenAffordable() {
        planet.setMetal(200);
        planet.setCrystal(100);
        boolean ok = service.checkAndDeduct(1L, 150, 50, 0);
        assertThat(ok).isTrue();
        assertThat(planet.getMetal()).isEqualTo(50);
        assertThat(planet.getCrystal()).isEqualTo(50);
    }

    @Test
    void energyDeficitHalvesProductionRates() {
        // High metal mine, no solar plant -> net energy negative.
        Building metalMine = new Building(1L, BuildingType.METAL_MINE, 10, 0);
        when(buildingRepository.findByPlanetId(1L)).thenReturn(List.of(metalMine));
        when(playerTechnologyRepository.findByPlayerIdAndTechnology(eq(1L), eq(Technology.ENERGY_TECH)))
                .thenReturn(Optional.empty());

        Map<String, Double> rates = service.getProductionRates(planet);

        assertThat(rates.get("netEnergy")).isNegative();
        // With deficit the metal rate is the penalised (halved) production figure.
        double expectedDeficitRate = balancer.getMetalProductionPerHour(10, 50, false);
        assertThat(rates.get("metalRate")).isEqualTo(expectedDeficitRate, within(0.0001));
    }

    @Test
    void sufficientSolarEnergyYieldsFullProduction() {
        Building metalMine = new Building(1L, BuildingType.METAL_MINE, 1, 0);
        Building solar = new Building(1L, BuildingType.SOLAR_PLANT, 20, 1);
        when(buildingRepository.findByPlanetId(1L)).thenReturn(List.of(metalMine, solar));
        when(playerTechnologyRepository.findByPlayerIdAndTechnology(anyLong(), eq(Technology.ENERGY_TECH)))
                .thenReturn(Optional.empty());

        Map<String, Double> rates = service.getProductionRates(planet);

        assertThat(rates.get("netEnergy")).isPositive();
        double expectedFullRate = balancer.getMetalProductionPerHour(1, 50, true);
        assertThat(rates.get("metalRate")).isEqualTo(expectedFullRate, within(0.0001));
    }
}
