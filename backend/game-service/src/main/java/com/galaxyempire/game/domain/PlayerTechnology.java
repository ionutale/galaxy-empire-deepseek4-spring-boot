package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "player_technology", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"player_id", "technology"})
})
public class PlayerTechnology {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Technology technology;

    @Column(nullable = false)
    private int level = 0;

    public PlayerTechnology() {}

    public PlayerTechnology(Long playerId, Technology technology) {
        this.playerId = playerId;
        this.technology = technology;
        this.level = 0;
    }

    public Long getId() { return id; }
    public Long getPlayerId() { return playerId; }
    public Technology getTechnology() { return technology; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
}
