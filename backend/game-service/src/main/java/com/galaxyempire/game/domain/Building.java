package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "building", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"planet_id", "grid_position"})
})
public class Building {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planet_id", nullable = false)
    private Long planetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "building_type", nullable = false, length = 32)
    private BuildingType buildingType;

    @Column(nullable = false)
    private int level = 0;

    @Column(name = "grid_position", nullable = false)
    private int gridPosition;

    public Building() {}

    public Building(Long planetId, BuildingType buildingType, int level, int gridPosition) {
        this.planetId = planetId;
        this.buildingType = buildingType;
        this.level = level;
        this.gridPosition = gridPosition;
    }

    public Long getId() { return id; }
    public Long getPlanetId() { return planetId; }
    public BuildingType getBuildingType() { return buildingType; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getGridPosition() { return gridPosition; }
}
