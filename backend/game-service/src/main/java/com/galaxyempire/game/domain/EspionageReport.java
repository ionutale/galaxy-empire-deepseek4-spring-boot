package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "espionage_report")
public class EspionageReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attacker_id", nullable = false)
    private Long attackerId;

    @Column(name = "defender_id", nullable = false)
    private Long defenderId;

    @Column(name = "target_planet_id", nullable = false)
    private Long targetPlanetId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "resources_json", columnDefinition = "jsonb", nullable = false)
    private String resourcesJson;

    @Column(name = "ships_json", columnDefinition = "jsonb", nullable = false)
    private String shipsJson;

    @Column(name = "buildings_json", columnDefinition = "jsonb", nullable = false)
    private String buildingsJson;

    @Column(name = "technologies_json", columnDefinition = "jsonb", nullable = false)
    private String technologiesJson;

    @Column(name = "defenses_json", columnDefinition = "jsonb", nullable = false)
    private String defensesJson;

    public EspionageReport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAttackerId() { return attackerId; }
    public void setAttackerId(Long attackerId) { this.attackerId = attackerId; }
    public Long getDefenderId() { return defenderId; }
    public void setDefenderId(Long defenderId) { this.defenderId = defenderId; }
    public Long getTargetPlanetId() { return targetPlanetId; }
    public void setTargetPlanetId(Long targetPlanetId) { this.targetPlanetId = targetPlanetId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getResourcesJson() { return resourcesJson; }
    public void setResourcesJson(String resourcesJson) { this.resourcesJson = resourcesJson; }
    public String getShipsJson() { return shipsJson; }
    public void setShipsJson(String shipsJson) { this.shipsJson = shipsJson; }
    public String getBuildingsJson() { return buildingsJson; }
    public void setBuildingsJson(String buildingsJson) { this.buildingsJson = buildingsJson; }
    public String getTechnologiesJson() { return technologiesJson; }
    public void setTechnologiesJson(String technologiesJson) { this.technologiesJson = technologiesJson; }
    public String getDefensesJson() { return defensesJson; }
    public void setDefensesJson(String defensesJson) { this.defensesJson = defensesJson; }
}
