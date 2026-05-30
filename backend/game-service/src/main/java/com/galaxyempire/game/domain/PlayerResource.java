package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "player_resource")
public class PlayerResource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false, unique = true)
    private Long playerId;

    @Column(name = "dark_matter", nullable = false)
    private int darkMatter = 0;

    public PlayerResource() {}

    public PlayerResource(Long playerId) {
        this.playerId = playerId;
        this.darkMatter = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public int getDarkMatter() { return darkMatter; }
    public void setDarkMatter(int darkMatter) { this.darkMatter = darkMatter; }
}
