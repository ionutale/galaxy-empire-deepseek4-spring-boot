package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "fleet_ship")
public class FleetShip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fleet_id", nullable = false)
    private Long fleetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ship_type", nullable = false, length = 32)
    private ShipType shipType;

    @Column(nullable = false)
    private int quantity;

    public FleetShip() {}

    public FleetShip(Long fleetId, ShipType shipType, int quantity) {
        this.fleetId = fleetId;
        this.shipType = shipType;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFleetId() { return fleetId; }
    public void setFleetId(Long fleetId) { this.fleetId = fleetId; }
    public ShipType getShipType() { return shipType; }
    public void setShipType(ShipType shipType) { this.shipType = shipType; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
