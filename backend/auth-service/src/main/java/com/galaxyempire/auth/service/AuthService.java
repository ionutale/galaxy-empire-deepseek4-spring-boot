package com.galaxyempire.auth.service;

import com.galaxyempire.auth.config.JwtUtil;
import com.galaxyempire.auth.domain.Player;
import com.galaxyempire.auth.repository.PlayerRepository;
import com.galaxyempire.auth.web.AuthResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(PlayerRepository playerRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (playerRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }

        Player player = new Player(username, passwordEncoder.encode(password));
        player = playerRepository.save(player);

        String token = jwtUtil.generateToken(player.getId(), player.getUsername());
        return new AuthResponse(token, player.getId(), player.getUsername());
    }

    public AuthResponse login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }

        Player player = playerRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(password, player.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(player.getId(), player.getUsername());
        return new AuthResponse(token, player.getId(), player.getUsername());
    }
}
