package com.galaxyempire.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "player")
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "home_planet_id")
    private Long homePlanetId;

    @Column(name = "dark_matter", nullable = false)
    private int darkMatter = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Player() {}

    public Player(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Long getHomePlanetId() { return homePlanetId; }
    public void setHomePlanetId(Long homePlanetId) { this.homePlanetId = homePlanetId; }
    public int getDarkMatter() { return darkMatter; }
    public void setDarkMatter(int darkMatter) { this.darkMatter = darkMatter; }
    public Instant getCreatedAt() { return createdAt; }
}
