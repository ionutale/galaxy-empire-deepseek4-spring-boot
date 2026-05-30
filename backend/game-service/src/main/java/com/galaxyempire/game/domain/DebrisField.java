package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "debris_field")
public class DebrisField {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planet_id", nullable = false, unique = true)
    private Long planetId;

    @Column(nullable = false)
    private double metal;

    @Column(nullable = false)
    private double crystal;

    public DebrisField() {}

    public DebrisField(Long planetId) {
        this.planetId = planetId;
        this.metal = 0;
        this.crystal = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanetId() { return planetId; }
    public void setPlanetId(Long planetId) { this.planetId = planetId; }
    public double getMetal() { return metal; }
    public void setMetal(double metal) { this.metal = metal; }
    public double getCrystal() { return crystal; }
    public void setCrystal(double crystal) { this.crystal = crystal; }
    public void addMetal(double amount) { this.metal += amount; }
    public void addCrystal(double amount) { this.crystal += amount; }
}
