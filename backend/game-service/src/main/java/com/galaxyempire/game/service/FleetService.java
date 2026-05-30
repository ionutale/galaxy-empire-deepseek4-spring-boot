package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;

@Service
public class FleetService {

    private final FleetRepository fleetRepository;
    private final FleetShipRepository fleetShipRepository;
    private final CombatReportRepository combatReportRepository;
    private final DebrisFieldRepository debrisFieldRepository;
    private final PlanetShipRepository planetShipRepository;
    private final PlanetRepository planetRepository;
    private final CombatService combatService;
    private final GameBalancer gameBalancer;
    private final EspionageReportRepository espionageReportRepository;
    private final PlayerTechnologyRepository playerTechnologyRepository;
    private final BuildingRepository buildingRepository;
    private final EconomyService economyService;

    public FleetService(FleetRepository fleetRepository,
                        FleetShipRepository fleetShipRepository,
                        CombatReportRepository combatReportRepository,
                        DebrisFieldRepository debrisFieldRepository,
                        PlanetShipRepository planetShipRepository,
                        PlanetRepository planetRepository,
                        CombatService combatService,
                        GameBalancer gameBalancer,
                        EspionageReportRepository espionageReportRepository,
                        PlayerTechnologyRepository playerTechnologyRepository,
                        BuildingRepository buildingRepository,
                        EconomyService economyService) {
        this.fleetRepository = fleetRepository;
        this.fleetShipRepository = fleetShipRepository;
        this.combatReportRepository = combatReportRepository;
        this.debrisFieldRepository = debrisFieldRepository;
        this.planetShipRepository = planetShipRepository;
        this.planetRepository = planetRepository;
        this.combatService = combatService;
        this.gameBalancer = gameBalancer;
        this.espionageReportRepository = espionageReportRepository;
        this.playerTechnologyRepository = playerTechnologyRepository;
        this.buildingRepository = buildingRepository;
        this.economyService = economyService;
    }

    @Transactional
    public Map<String, Object> launchFleet(Long originPlanetId, Long targetPlanetId,
                                            FleetMission mission, Map<String, Integer> ships,
                                            Long playerId, Map<String, Object> missionParams) {
        Planet origin = planetRepository.findById(originPlanetId)
            .orElseThrow(() -> new IllegalArgumentException("Origin planet not found"));
        if (!origin.getPlayerId().equals(playerId)) {
            throw new IllegalArgumentException("Origin planet does not belong to player");
        }
        if (originPlanetId.equals(targetPlanetId)) {
            throw new IllegalArgumentException("Target must be a different planet");
        }
        if (ships == null || ships.isEmpty()) {
            throw new IllegalArgumentException("Must send at least one ship");
        }

        if (mission == FleetMission.DEPLOY) {
            Planet target = planetRepository.findById(targetPlanetId)
                .orElseThrow(() -> new IllegalArgumentException("Target planet not found"));
            if (!target.getPlayerId().equals(playerId)) {
                throw new IllegalArgumentException("Can only deploy to own planets");
            }
        }

        if (mission == FleetMission.TRANSPORT) {
            Planet target = planetRepository.findById(targetPlanetId)
                .orElseThrow(() -> new IllegalArgumentException("Target planet not found"));
            if (!target.getPlayerId().equals(playerId)) {
                throw new IllegalArgumentException("Can only transport to own planets");
            }
            double metal = Double.parseDouble(missionParams.getOrDefault("metal", "0").toString());
            double crystal = Double.parseDouble(missionParams.getOrDefault("crystal", "0").toString());
            double gas = Double.parseDouble(missionParams.getOrDefault("gas", "0").toString());
            if (metal <= 0 && crystal <= 0 && gas <= 0) {
                throw new IllegalArgumentException("Must transport at least one resource type");
            }
            double totalCargo = ships.entrySet().stream()
                .mapToDouble(e -> gameBalancer.getShipCargo(ShipType.valueOf(e.getKey())) * e.getValue())
                .sum();
            if (metal + crystal + gas > totalCargo) {
                throw new IllegalArgumentException("Resource amount exceeds cargo capacity");
            }
            if (!economyService.checkAndDeduct(originPlanetId, metal, crystal, gas)) {
                throw new IllegalArgumentException("Insufficient resources at origin planet");
            }
        }

        if (mission == FleetMission.COLONIZE) {
            boolean hasColonyShip = ships.entrySet().stream()
                .anyMatch(e -> ShipType.valueOf(e.getKey()) == ShipType.COLONY_SHIP && e.getValue() > 0);
            if (!hasColonyShip) {
                throw new IllegalArgumentException("Colonize mission requires at least 1 Colony Ship");
            }
        }

        if (mission == FleetMission.SPY) {
            Planet target = planetRepository.findById(targetPlanetId)
                .orElseThrow(() -> new IllegalArgumentException("Target planet not found"));
            if (target.getPlayerId().equals(playerId)) {
                throw new IllegalArgumentException("Cannot spy on own planet");
            }
            boolean hasProbe = ships.entrySet().stream()
                .anyMatch(e -> ShipType.valueOf(e.getKey()) == ShipType.ESPIONAGE_PROBE && e.getValue() > 0);
            if (!hasProbe) {
                throw new IllegalArgumentException("Spy mission requires at least 1 Espionage Probe");
            }
        }

        if (mission == FleetMission.RECYCLE) {
            Optional<DebrisField> df = debrisFieldRepository.findByPlanetId(targetPlanetId);
            if (df.isEmpty() || (df.get().getMetal() <= 0 && df.get().getCrystal() <= 0)) {
                throw new IllegalArgumentException("No debris field at target planet");
            }
            boolean hasRecycler = ships.entrySet().stream()
                .anyMatch(e -> ShipType.valueOf(e.getKey()) == ShipType.RECYCLER && e.getValue() > 0);
            if (!hasRecycler) {
                throw new IllegalArgumentException("Recycle mission requires at least 1 Recycler");
            }
        }

        List<PlanetShip> originShips = planetShipRepository.findByPlanetId(originPlanetId);
        for (Map.Entry<String, Integer> entry : ships.entrySet()) {
            ShipType type = ShipType.valueOf(entry.getKey().toUpperCase());
            int quantity = entry.getValue();
            PlanetShip ps = originShips.stream()
                .filter(s -> s.getShipType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No " + type + " at origin planet"));
            if (ps.getQuantity() < quantity) {
                throw new IllegalArgumentException("Insufficient " + type + " at origin planet");
            }
            ps.setQuantity(ps.getQuantity() - quantity);
            planetShipRepository.save(ps);
        }

        int slowestSpeed = ships.keySet().stream()
            .mapToInt(k -> gameBalancer.getShipSpeed(ShipType.valueOf(k)))
            .min()
            .orElse(100);
        int travelTimeSecs = gameBalancer.getTravelTimeSeconds(1);

        Fleet fleet = new Fleet();
        fleet.setOriginPlanetId(originPlanetId);
        fleet.setTargetPlanetId(targetPlanetId);
        fleet.setPlayerId(playerId);
        fleet.setMission(mission);
        fleet.setDepartureTime(Instant.now());
        fleet.setArrivalTime(Instant.now().plusSeconds(travelTimeSecs));
        fleet.setReturnTime(null);
        fleet.setStatus(FleetStatus.EN_ROUTE);
        if (mission == FleetMission.TRANSPORT) {
            fleet.setMetalLoot(Double.parseDouble(missionParams.getOrDefault("metal", "0").toString()));
            fleet.setCrystalLoot(Double.parseDouble(missionParams.getOrDefault("crystal", "0").toString()));
            fleet.setGasLoot(Double.parseDouble(missionParams.getOrDefault("gas", "0").toString()));
        }
        fleet = fleetRepository.save(fleet);

        for (Map.Entry<String, Integer> entry : ships.entrySet()) {
            ShipType type = ShipType.valueOf(entry.getKey().toUpperCase());
            int quantity = entry.getValue();
            fleetShipRepository.save(new FleetShip(fleet.getId(), type, quantity));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fleetId", fleet.getId());
        result.put("mission", mission.name());
        result.put("arrivalTime", fleet.getArrivalTime().toString());
        result.put("travelTimeSeconds", travelTimeSecs);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFleetDetail(Long fleetId) {
        Fleet fleet = fleetRepository.findById(fleetId)
            .orElseThrow(() -> new IllegalArgumentException("Fleet not found"));
        List<FleetShip> ships = fleetShipRepository.findByFleetId(fleetId);
        Map<String, Object> result = new HashMap<>();
        result.put("fleet", fleet);
        Map<String, Integer> shipMap = new HashMap<>();
        for (FleetShip fs : ships) {
            shipMap.put(fs.getShipType().name(), fs.getQuantity());
        }
        result.put("ships", shipMap);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Fleet> getPlanetFleets(Long planetId) {
        return fleetRepository.findByOriginPlanetId(planetId);
    }

    @Transactional
    public void recallFleet(Long fleetId, Long playerId) {
        Fleet fleet = fleetRepository.findById(fleetId)
            .orElseThrow(() -> new IllegalArgumentException("Fleet not found"));
        if (!fleet.getPlayerId().equals(playerId)) {
            throw new IllegalArgumentException("Fleet does not belong to player");
        }
        if (fleet.getStatus() != FleetStatus.EN_ROUTE) {
            throw new IllegalArgumentException("Fleet cannot be recalled");
        }
        fleet.setStatus(FleetStatus.RETURNING);
        long travelTime = java.time.Duration.between(fleet.getDepartureTime(), fleet.getArrivalTime()).getSeconds();
        fleet.setReturnTime(Instant.now().plusSeconds(travelTime));
        fleetRepository.save(fleet);
    }

    @Transactional
    public void processArrivals(Instant now) {
        List<Fleet> arrivals = fleetRepository
            .findByStatusAndArrivalTimeLessThanEqual(FleetStatus.EN_ROUTE, now);
        for (Fleet fleet : arrivals) {
            try {
                List<FleetShip> ships = fleetShipRepository.findByFleetId(fleet.getId());
                if (ships.isEmpty()) {
                    fleet.setStatus(FleetStatus.RETURNING);
                    fleet.setReturnTime(Instant.now());
                    fleetRepository.save(fleet);
                    continue;
                }

                if (fleet.getMission() == FleetMission.ATTACK) {
                    CombatReport report = combatService.resolveCombat(fleet, ships);
                    List<FleetShip> currentShips = fleetShipRepository.findByFleetId(fleet.getId());
                    boolean anySurvivors = currentShips.stream().anyMatch(fs -> fs.getQuantity() > 0);
                    if (anySurvivors) {
                        fleet.setStatus(FleetStatus.RETURNING);
                        long travelTime = java.time.Duration.between(fleet.getDepartureTime(), fleet.getArrivalTime()).getSeconds();
                        fleet.setReturnTime(Instant.now().plusSeconds(travelTime));
                    } else {
                        fleet.setStatus(FleetStatus.ARRIVED);
                        fleet.setReturnTime(null);
                    }
                    fleetRepository.save(fleet);
                } else if (fleet.getMission() == FleetMission.DEPLOY) {
                    for (FleetShip fs : ships) {
                        PlanetShip ps = planetShipRepository
                            .findByPlanetIdAndShipType(fleet.getTargetPlanetId(), fs.getShipType())
                            .orElseGet(() -> planetShipRepository.save(
                                new PlanetShip(fleet.getTargetPlanetId(), fs.getShipType())));
                        ps.addQuantity(fs.getQuantity());
                        planetShipRepository.save(ps);
                    }
                    fleet.setStatus(FleetStatus.ARRIVED);
                    fleet.setReturnTime(null);
                    fleetRepository.save(fleet);
                } else if (fleet.getMission() == FleetMission.TRANSPORT) {
                    Planet target = planetRepository.findById(fleet.getTargetPlanetId()).orElse(null);
                    if (target != null) {
                        economyService.addResources(fleet.getTargetPlanetId(), fleet.getMetalLoot(), fleet.getCrystalLoot(), fleet.getGasLoot());
                    }
                    fleet.setStatus(FleetStatus.RETURNING);
                    long travelTime = java.time.Duration.between(fleet.getDepartureTime(), fleet.getArrivalTime()).getSeconds();
                    fleet.setReturnTime(Instant.now().plusSeconds(travelTime));
                    fleetRepository.save(fleet);
                } else if (fleet.getMission() == FleetMission.COLONIZE) {
                    for (FleetShip fs : ships) {
                        if (fs.getShipType() == ShipType.COLONY_SHIP && fs.getQuantity() > 0) {
                            fs.setQuantity(fs.getQuantity() - 1);
                            fleetShipRepository.save(fs);
                            break;
                        }
                    }
                    boolean anySurvivors = ships.stream().anyMatch(fs -> fs.getQuantity() > 0);
                    if (anySurvivors) {
                        fleet.setStatus(FleetStatus.RETURNING);
                        long travelTime = java.time.Duration.between(fleet.getDepartureTime(), fleet.getArrivalTime()).getSeconds();
                        fleet.setReturnTime(Instant.now().plusSeconds(travelTime));
                    } else {
                        fleet.setStatus(FleetStatus.ARRIVED);
                        fleet.setReturnTime(null);
                    }
                    fleetRepository.save(fleet);
                } else if (fleet.getMission() == FleetMission.SPY) {
                    int attackerEspLevel = playerTechnologyRepository
                        .findByPlayerIdAndTechnology(fleet.getPlayerId(), Technology.ESPIONAGE_TECH)
                        .map(PlayerTechnology::getLevel)
                        .orElse(0);
                    Planet targetPlanet = planetRepository.findById(fleet.getTargetPlanetId()).orElse(null);
                    int defenderEspLevel = 0;
                    if (targetPlanet != null) {
                        defenderEspLevel = playerTechnologyRepository
                            .findByPlayerIdAndTechnology(targetPlanet.getPlayerId(), Technology.ESPIONAGE_TECH)
                            .map(PlayerTechnology::getLevel)
                            .orElse(0);
                    }
                    for (FleetShip fs : ships) {
                        fs.setQuantity(0);
                        fleetShipRepository.save(fs);
                    }
                    if (attackerEspLevel > defenderEspLevel && targetPlanet != null) {
                        EspionageReport report = new EspionageReport();
                        report.setAttackerId(fleet.getPlayerId());
                        report.setDefenderId(targetPlanet.getPlayerId());
                        report.setTargetPlanetId(fleet.getTargetPlanetId());
                        report.setTimestamp(Instant.now());
                        int diff = attackerEspLevel - defenderEspLevel;
                        ObjectMapper mapper = new ObjectMapper();
                        try {
                            report.setResourcesJson(mapper.writeValueAsString(Map.of(
                                "metal", targetPlanet.getMetal(),
                                "crystal", targetPlanet.getCrystal(),
                                "gas", targetPlanet.getGas()
                            )));
                            if (diff >= 1) {
                                List<PlanetShip> planetShips = planetShipRepository.findByPlanetId(fleet.getTargetPlanetId());
                                Map<String, Integer> shipMap = new HashMap<>();
                                for (PlanetShip ps : planetShips) {
                                    if (ps.getQuantity() > 0) {
                                        shipMap.put(ps.getShipType().name(), ps.getQuantity());
                                    }
                                }
                                report.setShipsJson(mapper.writeValueAsString(shipMap));
                            }
                            if (diff >= 2) {
                                List<Building> buildings = buildingRepository.findByPlanetId(fleet.getTargetPlanetId());
                                Map<String, Integer> buildingMap = new HashMap<>();
                                for (Building b : buildings) {
                                    if (b.getLevel() > 0) {
                                        buildingMap.put(b.getBuildingType().name(), b.getLevel());
                                    }
                                }
                                report.setBuildingsJson(mapper.writeValueAsString(buildingMap));
                            }
                            if (diff >= 3) {
                                List<PlayerTechnology> techs = playerTechnologyRepository.findByPlayerId(targetPlanet.getPlayerId());
                                Map<String, Integer> techMap = new HashMap<>();
                                for (PlayerTechnology pt : techs) {
                                    if (pt.getLevel() > 0) {
                                        techMap.put(pt.getTechnology().name(), pt.getLevel());
                                    }
                                }
                                report.setTechnologiesJson(mapper.writeValueAsString(techMap));
                            }
                        } catch (Exception ignored) {}
                        espionageReportRepository.save(report);
                    }
                    fleet.setStatus(FleetStatus.RETURNING);
                    fleet.setReturnTime(Instant.now());
                    fleetRepository.save(fleet);
                } else if (fleet.getMission() == FleetMission.RECYCLE) {
                    Optional<DebrisField> dfOpt = debrisFieldRepository.findByPlanetId(fleet.getTargetPlanetId());
                    if (dfOpt.isPresent()) {
                        DebrisField df = dfOpt.get();
                        int recyclerCount = 0;
                        for (FleetShip fs : ships) {
                            if (fs.getShipType() == ShipType.RECYCLER) {
                                recyclerCount += fs.getQuantity();
                            }
                        }
                        double cargoCapacity = recyclerCount * gameBalancer.getShipCargo(ShipType.RECYCLER);
                        double collectMetal = Math.min(df.getMetal(), cargoCapacity);
                        double remaining = cargoCapacity - collectMetal;
                        double collectCrystal = Math.min(df.getCrystal(), remaining);
                        fleet.setMetalLoot(collectMetal);
                        fleet.setCrystalLoot(collectCrystal);
                        df.setMetal(df.getMetal() - collectMetal);
                        df.setCrystal(df.getCrystal() - collectCrystal);
                        debrisFieldRepository.save(df);
                    }
                    fleet.setStatus(FleetStatus.RETURNING);
                    long travelTime = java.time.Duration.between(fleet.getDepartureTime(), fleet.getArrivalTime()).getSeconds();
                    fleet.setReturnTime(Instant.now().plusSeconds(travelTime));
                    fleetRepository.save(fleet);
                }
            } catch (Exception e) {
                System.err.println("Failed to process fleet " + fleet.getId() + ": " + e.getMessage());
                fleet.setStatus(FleetStatus.ARRIVED);
                fleetRepository.save(fleet);
            }
        }
    }

    @Transactional
    public void processReturns(Instant now) {
        List<Fleet> returns = fleetRepository
            .findByStatusAndReturnTimeLessThanEqual(FleetStatus.RETURNING, now);
        for (Fleet fleet : returns) {
            try {
                List<FleetShip> ships = fleetShipRepository.findByFleetId(fleet.getId());
                for (FleetShip fs : ships) {
                    if (fs.getQuantity() > 0) {
                        PlanetShip ps = planetShipRepository
                            .findByPlanetIdAndShipType(fleet.getOriginPlanetId(), fs.getShipType())
                            .orElseGet(() -> planetShipRepository.save(
                                new PlanetShip(fleet.getOriginPlanetId(), fs.getShipType())));
                        ps.addQuantity(fs.getQuantity());
                        planetShipRepository.save(ps);
                    }
                }
                if (fleet.getMetalLoot() > 0 || fleet.getCrystalLoot() > 0 || fleet.getGasLoot() > 0) {
                    economyService.addResources(fleet.getOriginPlanetId(), fleet.getMetalLoot(), fleet.getCrystalLoot(), fleet.getGasLoot());
                }
                fleet.setStatus(FleetStatus.ARRIVED);
                fleet.setReturnTime(null);
                fleetRepository.save(fleet);
            } catch (Exception e) {
                System.err.println("Failed to process return " + fleet.getId() + ": " + e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<CombatReport> getPlanetCombatReports(Long planetId) {
        return combatReportRepository.findByPlanetId(planetId);
    }

    @Transactional(readOnly = true)
    public CombatReport getCombatReport(Long reportId) {
        return combatReportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Combat report not found"));
    }

    @Transactional(readOnly = true)
    public Optional<DebrisField> getDebrisField(Long planetId) {
        return debrisFieldRepository.findByPlanetId(planetId);
    }

    @Transactional(readOnly = true)
    public List<EspionageReport> getPlanetEspionageReports(Long planetId) {
        return espionageReportRepository.findByTargetPlanetIdOrderByTimestampDesc(planetId);
    }
}
