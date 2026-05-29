package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "planet", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"galaxy", "system_id", "slot"})
})
public class Planet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(length = 64, nullable = false)
    private String name;

    @Column(nullable = false)
    private int galaxy;

    @Column(name = "system_id", nullable = false)
    private int systemId;

    @Column(nullable = false)
    private int slot;

    @Column(nullable = false)
    private double metal = 500;

    @Column(nullable = false)
    private double crystal = 500;

    @Column(nullable = false)
    private double gas = 500;

    @Column(nullable = false)
    private double energy = 0;

    @Column(name = "temperature", nullable = false)
    private int temperature;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Planet() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getGalaxy() { return galaxy; }
    public void setGalaxy(int galaxy) { this.galaxy = galaxy; }
    public int getSystemId() { return systemId; }
    public void setSystemId(int systemId) { this.systemId = systemId; }
    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }
    public double getMetal() { return metal; }
    public void setMetal(double metal) { this.metal = metal; }
    public double getCrystal() { return crystal; }
    public void setCrystal(double crystal) { this.crystal = crystal; }
    public double getGas() { return gas; }
    public void setGas(double gas) { this.gas = gas; }
    public double getEnergy() { return energy; }
    public void setEnergy(double energy) { this.energy = energy; }
    public int getTemperature() { return temperature; }
    public void setTemperature(int temperature) { this.temperature = temperature; }
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    public Instant getCreatedAt() { return createdAt; }
}
