package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "combat_report")
public class CombatReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attacker_id", nullable = false)
    private Long attackerId;

    @Column(name = "defender_id", nullable = false)
    private Long defenderId;

    @Column(name = "attacker_planet_id", nullable = false)
    private Long attackerPlanetId;

    @Column(name = "defender_planet_id", nullable = false)
    private Long defenderPlanetId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false, length = 16)
    private String result;

    @Column(name = "attacker_ships_before", columnDefinition = "jsonb", nullable = false)
    private String attackerShipsBefore;

    @Column(name = "defender_ships_before", columnDefinition = "jsonb", nullable = false)
    private String defenderShipsBefore;

    @Column(name = "attacker_ships_lost", columnDefinition = "jsonb", nullable = false)
    private String attackerShipsLost;

    @Column(name = "defender_ships_lost", columnDefinition = "jsonb", nullable = false)
    private String defenderShipsLost;

    @Column(name = "debris_metal", nullable = false)
    private double debrisMetal;

    @Column(name = "debris_crystal", nullable = false)
    private double debrisCrystal;

    @Column(name = "resources_looted", columnDefinition = "jsonb", nullable = false)
    private String resourcesLooted;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String rounds;

    public CombatReport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAttackerId() { return attackerId; }
    public void setAttackerId(Long attackerId) { this.attackerId = attackerId; }
    public Long getDefenderId() { return defenderId; }
    public void setDefenderId(Long defenderId) { this.defenderId = defenderId; }
    public Long getAttackerPlanetId() { return attackerPlanetId; }
    public void setAttackerPlanetId(Long attackerPlanetId) { this.attackerPlanetId = attackerPlanetId; }
    public Long getDefenderPlanetId() { return defenderPlanetId; }
    public void setDefenderPlanetId(Long defenderPlanetId) { this.defenderPlanetId = defenderPlanetId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getAttackerShipsBefore() { return attackerShipsBefore; }
    public void setAttackerShipsBefore(String attackerShipsBefore) { this.attackerShipsBefore = attackerShipsBefore; }
    public String getDefenderShipsBefore() { return defenderShipsBefore; }
    public void setDefenderShipsBefore(String defenderShipsBefore) { this.defenderShipsBefore = defenderShipsBefore; }
    public String getAttackerShipsLost() { return attackerShipsLost; }
    public void setAttackerShipsLost(String attackerShipsLost) { this.attackerShipsLost = attackerShipsLost; }
    public String getDefenderShipsLost() { return defenderShipsLost; }
    public void setDefenderShipsLost(String defenderShipsLost) { this.defenderShipsLost = defenderShipsLost; }
    public double getDebrisMetal() { return debrisMetal; }
    public void setDebrisMetal(double debrisMetal) { this.debrisMetal = debrisMetal; }
    public double getDebrisCrystal() { return debrisCrystal; }
    public void setDebrisCrystal(double debrisCrystal) { this.debrisCrystal = debrisCrystal; }
    public String getResourcesLooted() { return resourcesLooted; }
    public void setResourcesLooted(String resourcesLooted) { this.resourcesLooted = resourcesLooted; }
    public String getRounds() { return rounds; }
    public void setRounds(String rounds) { this.rounds = rounds; }
}
