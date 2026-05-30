package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "research_queue")
public class ResearchQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Technology technology;

    @Column(name = "target_level", nullable = false)
    private int targetLevel;

    @Column(name = "metal_cost", nullable = false)
    private double metalCost;

    @Column(name = "crystal_cost", nullable = false)
    private double crystalCost;

    @Column(name = "gas_cost", nullable = false)
    private double gasCost;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completes_at", nullable = false)
    private Instant completesAt;

    @Column(nullable = false)
    private boolean completed = false;

    public ResearchQueue() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public Technology getTechnology() { return technology; }
    public void setTechnology(Technology technology) { this.technology = technology; }
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
