package com.galaxyempire.auth.web;

public class AuthResponse {
    private String token;
    private Long playerId;
    private String username;

    public AuthResponse() {}

    public AuthResponse(String token, Long playerId, String username) {
        this.token = token;
        this.playerId = playerId;
        this.username = username;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
