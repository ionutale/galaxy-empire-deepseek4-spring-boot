package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "construction_queue")
public class ConstructionQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planet_id", nullable = false)
    private Long planetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "building_type", nullable = false, length = 32)
    private BuildingType buildingType;

    @Column(name = "target_level", nullable = false)
    private int targetLevel;

    @Column(name = "metal_cost", nullable = false)
    private double metalCost;

    @Column(name = "crystal_cost", nullable = false)
    private double crystalCost;

    @Column(name = "gas_cost", nullable = false)
    private double gasCost;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completes_at", nullable = false)
    private Instant completesAt;

    @Column(nullable = false)
    private boolean completed = false;

    public ConstructionQueue() {}

    public Long getId() { return id; }
    public Long getPlanetId() { return planetId; }
    public void setPlanetId(Long planetId) { this.planetId = planetId; }
    public BuildingType getBuildingType() { return buildingType; }
    public void setBuildingType(BuildingType buildingType) { this.buildingType = buildingType; }
    public int getTargetLevel() { return targetLevel; }
    public void setTargetLevel(int targetLevel) { this.targetLevel = targetLevel; }
    public double getMetalCost() { return metalCost; }
    public void setMetalCost(double metalCost) { this.metalCost = metalCost; }
    public double getCrystalCost() { return crystalCost; }
    public void setCrystalCost(double crystalCost) { this.crystalCost = crystalCost; }
    public double getGasCost() { return gasCost; }
    public void setGasCost(double gasCost) { this.gasCost = gasCost; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletesAt() { return completesAt; }
    public void setCompletesAt(Instant completesAt) { this.completesAt = completesAt; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
