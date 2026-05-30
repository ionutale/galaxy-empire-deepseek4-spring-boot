package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PlanetService {

    private final PlanetRepository planetRepository;
    private final BuildingRepository buildingRepository;
    private final GameBalancer balancer;
    private final ResourceService resourceService;
    private final PlayerTechnologyRepository playerTechnologyRepository;

    public PlanetService(PlanetRepository planetRepository, BuildingRepository buildingRepository,
                         GameBalancer balancer, ResourceService resourceService,
                         PlayerTechnologyRepository playerTechnologyRepository) {
        this.planetRepository = planetRepository;
        this.buildingRepository = buildingRepository;
        this.balancer = balancer;
        this.resourceService = resourceService;
        this.playerTechnologyRepository = playerTechnologyRepository;
    }

    @Transactional
    public Planet createStarterPlanet(Long playerId) {
        int galaxy = 1;
        int systemId = ThreadLocalRandom.current().nextInt(1, 501);
        int slot = ThreadLocalRandom.current().nextInt(1, 16);

        while (planetRepository.existsByGalaxyAndSystemIdAndSlot(galaxy, systemId, slot)) {
            systemId = ThreadLocalRandom.current().nextInt(1, 501);
            slot = ThreadLocalRandom.current().nextInt(1, 16);
        }

        int temperature = randomTemperature(slot);

        Planet planet = new Planet();
        planet.setPlayerId(playerId);
        planet.setName("Home Planet");
        planet.setGalaxy(galaxy);
        planet.setSystemId(systemId);
        planet.setSlot(slot);
        planet.setTemperature(temperature);
        planet = planetRepository.save(planet);

        List<Building> starters = Arrays.asList(
            new Building(planet.getId(), BuildingType.METAL_MINE, 1, 0),
            new Building(planet.getId(), BuildingType.CRYSTAL_MINE, 1, 1),
            new Building(planet.getId(), BuildingType.GAS_MINE, 1, 2),
            new Building(planet.getId(), BuildingType.SOLAR_PLANT, 1, 3),
            new Building(planet.getId(), BuildingType.METAL_STORAGE, 1, 4),
            new Building(planet.getId(), BuildingType.CRYSTAL_STORAGE, 1, 5),
            new Building(planet.getId(), BuildingType.GAS_STORAGE, 1, 6),
            new Building(planet.getId(), BuildingType.ROBOT_FACTORY, 0, 7),
            new Building(planet.getId(), BuildingType.RESEARCH_LAB, 0, 8),
            new Building(planet.getId(), BuildingType.SHIPYARD, 0, 9)
        );
        buildingRepository.saveAll(starters);

        return planet;
    }

    @Transactional
    public Planet createPlanetAt(Long playerId, int galaxy, int systemId, int slot) {
        if (galaxy < 1 || galaxy > 9 || systemId < 1 || systemId > 500 || slot < 1 || slot > 15) {
            throw new IllegalArgumentException("Invalid coordinates");
        }
        if (planetRepository.existsByGalaxyAndSystemIdAndSlot(galaxy, systemId, slot)) {
            throw new IllegalArgumentException("Planet already exists at these coordinates");
        }
        int temperature = randomTemperature(slot);
        Planet planet = new Planet();
        planet.setPlayerId(playerId);
        planet.setName("Colony");
        planet.setGalaxy(galaxy);
        planet.setSystemId(systemId);
        planet.setSlot(slot);
        planet.setTemperature(temperature);
        planet = planetRepository.save(planet);

        List<Building> starters = Arrays.asList(
            new Building(planet.getId(), BuildingType.METAL_MINE, 1, 0),
            new Building(planet.getId(), BuildingType.CRYSTAL_MINE, 1, 1),
            new Building(planet.getId(), BuildingType.GAS_MINE, 1, 2),
            new Building(planet.getId(), BuildingType.SOLAR_PLANT, 1, 3),
            new Building(planet.getId(), BuildingType.METAL_STORAGE, 1, 4),
            new Building(planet.getId(), BuildingType.CRYSTAL_STORAGE, 1, 5),
            new Building(planet.getId(), BuildingType.GAS_STORAGE, 1, 6),
            new Building(planet.getId(), BuildingType.ROBOT_FACTORY, 0, 7),
            new Building(planet.getId(), BuildingType.RESEARCH_LAB, 0, 8),
            new Building(planet.getId(), BuildingType.SHIPYARD, 0, 9)
        );
        buildingRepository.saveAll(starters);
        return planet;
    }

    private int randomTemperature(int slot) {
        return switch (slot) {
            case 1, 2, 3 -> ThreadLocalRandom.current().nextInt(40, 100);
            case 4, 5, 6 -> ThreadLocalRandom.current().nextInt(-10, 40);
            default -> ThreadLocalRandom.current().nextInt(-120, 0);
        };
    }

    public Planet getPlanetWithResources(Long planetId) {
        Planet planet = planetRepository.findById(planetId)
            .orElseThrow(() -> new IllegalArgumentException("Planet not found"));
        recalculate(planet);
        return planetRepository.save(planet);
    }

    public void recalculate(Planet planet) {
        List<Building> buildings = buildingRepository.findByPlanetId(planet.getId());

        double metalProd = 0, crystalProd = 0, gasProd = 0;
        double energyProduced = 0, energyConsumed = 0;

        for (Building b : buildings) {
            switch (b.getBuildingType()) {
                case METAL_MINE -> {
                    double p = balancer.getMetalProductionPerHour(b.getLevel(), planet.getTemperature(), true);
                    metalProd += p;
                    energyConsumed += balancer.getMineEnergyConsumption(BuildingType.METAL_MINE, b.getLevel());
                }
                case CRYSTAL_MINE -> {
                    double p = balancer.getCrystalProductionPerHour(b.getLevel(), planet.getTemperature(), true);
                    crystalProd += p;
                    energyConsumed += balancer.getMineEnergyConsumption(BuildingType.CRYSTAL_MINE, b.getLevel());
                }
                case GAS_MINE -> {
                    double p = balancer.getGasProductionPerHour(b.getLevel(), planet.getTemperature(), true);
                    gasProd += p;
                    energyConsumed += balancer.getMineEnergyConsumption(BuildingType.GAS_MINE, b.getLevel());
                }
                case SOLAR_PLANT -> energyProduced += balancer.getSolarPlantEnergy(b.getLevel());
                default -> {}
            }
        }

        int fusionLevel = buildings.stream()
            .filter(b -> b.getBuildingType() == BuildingType.FUSION_REACTOR)
            .mapToInt(Building::getLevel)
            .findFirst().orElse(0);
        int energyTechLevel = playerTechnologyRepository
            .findByPlayerIdAndTechnology(planet.getPlayerId(), Technology.ENERGY_TECH)
            .map(PlayerTechnology::getLevel)
            .orElse(0);
        double fusionEnergy = balancer.getFusionEnergy(fusionLevel, energyTechLevel);

        planet.setEnergy(energyProduced + fusionEnergy - energyConsumed);
        boolean energyPositive = planet.getEnergy() >= 0;

        metalProd = 0; crystalProd = 0; gasProd = 0;
        for (Building b : buildings) {
            switch (b.getBuildingType()) {
                case METAL_MINE -> metalProd += balancer.getMetalProductionPerHour(b.getLevel(), planet.getTemperature(), energyPositive);
                case CRYSTAL_MINE -> crystalProd += balancer.getCrystalProductionPerHour(b.getLevel(), planet.getTemperature(), energyPositive);
                case GAS_MINE -> gasProd += balancer.getGasProductionPerHour(b.getLevel(), planet.getTemperature(), energyPositive);
                default -> {}
            }
        }

        resourceService.recalculateResources(planet, metalProd, crystalProd, gasProd);
    }

    public Map<String, Object> getPlanetDetails(Long planetId) {
        Planet planet = getPlanetWithResources(planetId);
        List<Building> buildings = buildingRepository.findByPlanetId(planetId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", planet.getId());
        details.put("playerId", planet.getPlayerId());
        details.put("name", planet.getName());
        details.put("coordinates", planet.getGalaxy() + ":" + planet.getSystemId() + ":" + planet.getSlot());
        details.put("temperature", planet.getTemperature());
        details.put("resources", Map.of(
            "metal", planet.getMetal(),
            "crystal", planet.getCrystal(),
            "gas", planet.getGas(),
            "energy", planet.getEnergy()
        ));
        details.put("buildings", buildings);
        return details;
    }

    public List<Map<String, Object>> getPlanetsByPlayer(Long playerId) {
        List<Planet> planets = planetRepository.findByPlayerIdOrderByCreatedAt(playerId);
        return planets.stream().map(p -> Map.<String, Object>of(
            "id", p.getId(),
            "name", p.getName(),
            "coordinates", p.getGalaxy() + ":" + p.getSystemId() + ":" + p.getSlot()
        )).toList();
    }
}
