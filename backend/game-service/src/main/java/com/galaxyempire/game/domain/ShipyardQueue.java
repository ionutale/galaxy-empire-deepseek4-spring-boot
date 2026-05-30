package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;

import com.galaxyempire.game.domain.DefenseType;

@Entity
@Table(name = "shipyard_queue")
public class ShipyardQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planet_id", nullable = false)
    private Long planetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ship_type", length = 32)
    private ShipType shipType;

    @Enumerated(EnumType.STRING)
    @Column(name = "defense_type", length = 32)
    private DefenseType defenseType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "built_quantity", nullable = false)
    private int builtQuantity = 0;

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

    public ShipyardQueue() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanetId() { return planetId; }
    public void setPlanetId(Long planetId) { this.planetId = planetId; }
    public ShipType getShipType() { return shipType; }
    public void setShipType(ShipType shipType) { this.shipType = shipType; }
    public DefenseType getDefenseType() { return defenseType; }
    public void setDefenseType(DefenseType defenseType) { this.defenseType = defenseType; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getBuiltQuantity() { return builtQuantity; }
    public void setBuiltQuantity(int builtQuantity) { this.builtQuantity = builtQuantity; }
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
