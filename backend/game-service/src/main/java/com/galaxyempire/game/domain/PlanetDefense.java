package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "planet_defense", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"planet_id", "defense_type"})
})
public class PlanetDefense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planet_id", nullable = false)
    private Long planetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "defense_type", nullable = false, length = 32)
    private DefenseType defenseType;

    @Column(nullable = false)
    private int quantity = 0;

    public PlanetDefense() {}

    public PlanetDefense(Long planetId, DefenseType defenseType) {
        this.planetId = planetId;
        this.defenseType = defenseType;
        this.quantity = 0;
    }

    public Long getId() { return id; }
    public Long getPlanetId() { return planetId; }
    public DefenseType getDefenseType() { return defenseType; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void addQuantity(int amount) { this.quantity += amount; }
}
