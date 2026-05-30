package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fleet")
public class Fleet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "origin_planet_id", nullable = false)
    private Long originPlanetId;

    @Column(name = "target_planet_id", nullable = false)
    private Long targetPlanetId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FleetMission mission;

    @Column(name = "departure_time", nullable = false)
    private Instant departureTime;

    @Column(name = "arrival_time", nullable = false)
    private Instant arrivalTime;

    @Column(name = "return_time")
    private Instant returnTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FleetStatus status = FleetStatus.EN_ROUTE;

    @Column(name = "metal_loot", nullable = false)
    private double metalLoot;

    @Column(name = "crystal_loot", nullable = false)
    private double crystalLoot;

    @Column(name = "gas_loot", nullable = false)
    private double gasLoot;

    public Fleet() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOriginPlanetId() { return originPlanetId; }
    public void setOriginPlanetId(Long originPlanetId) { this.originPlanetId = originPlanetId; }
    public Long getTargetPlanetId() { return targetPlanetId; }
    public void setTargetPlanetId(Long targetPlanetId) { this.targetPlanetId = targetPlanetId; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public FleetMission getMission() { return mission; }
    public void setMission(FleetMission mission) { this.mission = mission; }
    public Instant getDepartureTime() { return departureTime; }
    public void setDepartureTime(Instant departureTime) { this.departureTime = departureTime; }
    public Instant getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(Instant arrivalTime) { this.arrivalTime = arrivalTime; }
    public Instant getReturnTime() { return returnTime; }
    public void setReturnTime(Instant returnTime) { this.returnTime = returnTime; }
    public FleetStatus getStatus() { return status; }
    public void setStatus(FleetStatus status) { this.status = status; }
    public double getMetalLoot() { return metalLoot; }
    public void setMetalLoot(double metalLoot) { this.metalLoot = metalLoot; }
    public double getCrystalLoot() { return crystalLoot; }
    public void setCrystalLoot(double crystalLoot) { this.crystalLoot = crystalLoot; }
    public double getGasLoot() { return gasLoot; }
    public void setGasLoot(double gasLoot) { this.gasLoot = gasLoot; }
}
