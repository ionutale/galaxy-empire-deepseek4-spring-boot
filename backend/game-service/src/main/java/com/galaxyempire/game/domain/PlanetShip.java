package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "planet_ship", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"planet_id", "ship_type"})
})
public class PlanetShip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planet_id", nullable = false)
    private Long planetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ship_type", nullable = false, length = 32)
    private ShipType shipType;

    @Column(nullable = false)
    private int quantity = 0;

    public PlanetShip() {}

    public PlanetShip(Long planetId, ShipType shipType) {
        this.planetId = planetId;
        this.shipType = shipType;
        this.quantity = 0;
    }

    public Long getId() { return id; }
    public Long getPlanetId() { return planetId; }
    public ShipType getShipType() { return shipType; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void addQuantity(int amount) { this.quantity += amount; }
}
