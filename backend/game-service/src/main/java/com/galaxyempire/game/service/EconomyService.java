package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class EconomyService {

    private final PlanetRepository planetRepository;
    private final BuildingRepository buildingRepository;
    private final GameBalancer gameBalancer;
    private final PlayerTechnologyRepository playerTechnologyRepository;

    public EconomyService(PlanetRepository planetRepository,
                          BuildingRepository buildingRepository,
                          GameBalancer gameBalancer,
                          PlayerTechnologyRepository playerTechnologyRepository) {
        this.planetRepository = planetRepository;
        this.buildingRepository = buildingRepository;
        this.gameBalancer = gameBalancer;
        this.playerTechnologyRepository = playerTechnologyRepository;
    }

    @Transactional
    public boolean checkAndDeduct(Long planetId, double metal, double crystal, double gas) {
        Planet planet = planetRepository.findById(planetId).orElseThrow();
        if (planet.getMetal() < metal || planet.getCrystal() < crystal || planet.getGas() < gas) {
            return false;
        }
        planet.setMetal(planet.getMetal() - metal);
        planet.setCrystal(planet.getCrystal() - crystal);
        planet.setGas(planet.getGas() - gas);
        planetRepository.save(planet);
        return true;
    }

    @Transactional
    public void refund(Long planetId, double metal, double crystal, double gas) {
        Planet planet = planetRepository.findById(planetId).orElseThrow();
        Map<String, Double> caps = getStorageCaps(planet);
        planet.setMetal(Math.min(planet.getMetal() + metal, caps.get("metalStorage")));
        planet.setCrystal(Math.min(planet.getCrystal() + crystal, caps.get("crystalStorage")));
        planet.setGas(Math.min(planet.getGas() + gas, caps.get("gasStorage")));
        planetRepository.save(planet);
    }

    @Transactional
    public void addResources(Long planetId, double metal, double crystal, double gas) {
        Planet planet = planetRepository.findById(planetId).orElseThrow();
        Map<String, Double> caps = getStorageCaps(planet);
        planet.setMetal(Math.min(planet.getMetal() + metal, caps.get("metalStorage")));
        planet.setCrystal(Math.min(planet.getCrystal() + crystal, caps.get("crystalStorage")));
        planet.setGas(Math.min(planet.getGas() + gas, caps.get("gasStorage")));
        planetRepository.save(planet);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentResources(Long planetId) {
        Planet planet = planetRepository.findById(planetId).orElseThrow();
        Map<String, Double> rates = getProductionRates(planet);
        Map<String, Double> caps = getStorageCaps(planet);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planetId", planetId);
        result.put("metal", planet.getMetal());
        result.put("crystal", planet.getCrystal());
        result.put("gas", planet.getGas());
        result.put("energy", rates.get("netEnergy"));
        result.put("metalRate", rates.get("metalRate"));
        result.put("crystalRate", rates.get("crystalRate"));
        result.put("gasRate", rates.get("gasRate"));
        result.put("metalStorage", caps.get("metalStorage"));
        result.put("crystalStorage", caps.get("crystalStorage"));
        result.put("gasStorage", caps.get("gasStorage"));
        result.put("energyConsumption", rates.get("energyConsumption"));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Double> getStorageCaps(Planet planet) {
        List<Building> buildings = buildingRepository.findByPlanetId(planet.getId());
        int metalStorageLevel = buildings.stream()
            .filter(b -> b.getBuildingType() == BuildingType.METAL_STORAGE)
            .mapToInt(Building::getLevel)
            .findFirst().orElse(0);
        int crystalStorageLevel = buildings.stream()
            .filter(b -> b.getBuildingType() == BuildingType.CRYSTAL_STORAGE)
            .mapToInt(Building::getLevel)
            .findFirst().orElse(0);
        int gasStorageLevel = buildings.stream()
            .filter(b -> b.getBuildingType() == BuildingType.GAS_STORAGE)
            .mapToInt(Building::getLevel)
            .findFirst().orElse(0);
        Map<String, Double> caps = new HashMap<>();
        caps.put("metalStorage", gameBalancer.getStorageCapacity(metalStorageLevel));
        caps.put("crystalStorage", gameBalancer.getStorageCapacity(crystalStorageLevel));
        caps.put("gasStorage", gameBalancer.getStorageCapacity(gasStorageLevel));
        return caps;
    }

    @Transactional(readOnly = true)
    public Map<String, Double> getProductionRates(Planet planet) {
        List<Building> buildings = buildingRepository.findByPlanetId(planet.getId());
        int metalMineLevel = buildings.stream().filter(b -> b.getBuildingType() == BuildingType.METAL_MINE).mapToInt(Building::getLevel).findFirst().orElse(0);
        int crystalMineLevel = buildings.stream().filter(b -> b.getBuildingType() == BuildingType.CRYSTAL_MINE).mapToInt(Building::getLevel).findFirst().orElse(0);
        int gasMineLevel = buildings.stream().filter(b -> b.getBuildingType() == BuildingType.GAS_MINE).mapToInt(Building::getLevel).findFirst().orElse(0);
        int solarPlantLevel = buildings.stream().filter(b -> b.getBuildingType() == BuildingType.SOLAR_PLANT).mapToInt(Building::getLevel).findFirst().orElse(0);
        int fusionLevel = buildings.stream().filter(b -> b.getBuildingType() == BuildingType.FUSION_REACTOR).mapToInt(Building::getLevel).findFirst().orElse(0);

        double solarEnergy = gameBalancer.getSolarPlantEnergy(solarPlantLevel);
        double mineConsumption = gameBalancer.getMineEnergyConsumption(metalMineLevel)
            + gameBalancer.getMineEnergyConsumption(crystalMineLevel)
            + gameBalancer.getMineEnergyConsumption(gasMineLevel);

        int energyTechLevel = playerTechnologyRepository
            .findByPlayerIdAndTechnology(planet.getPlayerId(), Technology.ENERGY_TECH)
            .map(PlayerTechnology::getLevel)
            .orElse(0);

        double fusionEnergy = 0;
        if (fusionLevel > 0) {
            fusionEnergy = gameBalancer.getFusionEnergy(fusionLevel, energyTechLevel);
        }

        double totalEnergy = solarEnergy + fusionEnergy;
        double netEnergy = totalEnergy - mineConsumption;
        boolean isDeficit = netEnergy < 0;

        double metalRate = gameBalancer.getMetalProductionPerHour(metalMineLevel, planet.getTemperature(), !isDeficit);
        double crystalRate = gameBalancer.getCrystalProductionPerHour(crystalMineLevel, planet.getTemperature(), !isDeficit);
        double gasRate = gameBalancer.getGasProductionPerHour(gasMineLevel, planet.getTemperature(), !isDeficit);

        Map<String, Double> result = new HashMap<>();
        result.put("metalRate", metalRate);
        result.put("crystalRate", crystalRate);
        result.put("gasRate", gasRate);
        result.put("netEnergy", netEnergy);
        result.put("energyConsumption", mineConsumption);
        result.put("fusionEnergy", fusionEnergy);
        return result;
    }

    @Transactional
    public void tickResources() {
        List<Planet> planets = planetRepository.findAll();
        for (Planet planet : planets) {
            Instant now = Instant.now();
            double hoursElapsed = ChronoUnit.SECONDS.between(planet.getLastUpdated(), now) / 3600.0;
            if (hoursElapsed <= 0) continue;

            Map<String, Double> rates = getProductionRates(planet);
            Map<String, Double> caps = getStorageCaps(planet);

            double metalRate = rates.get("metalRate");
            double crystalRate = rates.get("crystalRate");
            double gasRate = rates.get("gasRate");

            double metalAccrued = metalRate * hoursElapsed;
            double crystalAccrued = crystalRate * hoursElapsed;
            double gasAccrued = gasRate * hoursElapsed;

            planet.setMetal(Math.min(planet.getMetal() + metalAccrued, caps.get("metalStorage")));
            planet.setCrystal(Math.min(planet.getCrystal() + crystalAccrued, caps.get("crystalStorage")));
            double newGas = planet.getGas() + gasAccrued;
            planet.setGas(Math.min(Math.max(0, newGas), caps.get("gasStorage")));
            planet.setLastUpdated(now);
        }
        planetRepository.saveAll(planets);
    }
}
