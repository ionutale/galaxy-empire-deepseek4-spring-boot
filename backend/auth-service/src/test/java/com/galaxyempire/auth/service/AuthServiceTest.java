package com.galaxyempire.auth.service;

import com.galaxyempire.auth.config.JwtUtil;
import com.galaxyempire.auth.domain.Player;
import com.galaxyempire.auth.repository.PlayerRepository;
import com.galaxyempire.auth.web.AuthResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks private AuthService service;

    // --- register ---

    @Test
    void registerRejectsBlankUsername() {
        assertThatThrownBy(() -> service.register("  ", "pw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is required");
    }

    @Test
    void registerRejectsBlankPassword() {
        assertThatThrownBy(() -> service.register("alice", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password is required");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(playerRepository.existsByUsername("alice")).thenReturn(true);
        assertThatThrownBy(() -> service.register("alice", "pw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void registerHashesPasswordAndReturnsToken() {
        when(playerRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("pw")).thenReturn("hashed");
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken(any(), eq("alice"))).thenReturn("jwt-token");

        AuthResponse response = service.register("alice", "pw");

        verify(passwordEncoder).encode("pw");
        verify(playerRepository).save(argThat(p -> "hashed".equals(p.getPasswordHash())));
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUsername()).isEqualTo("alice");
    }

    // --- login ---

    @Test
    void loginRejectsUnknownUsername() {
        when(playerRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("ghost", "pw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void loginRejectsWrongPassword() {
        Player player = new Player("alice", "hashed");
        when(playerRepository.findByUsername("alice")).thenReturn(Optional.of(player));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login("alice", "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid username or password");
        verify(jwtUtil, never()).generateToken(anyLong(), anyString());
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        Player player = new Player("alice", "hashed");
        when(playerRepository.findByUsername("alice")).thenReturn(Optional.of(player));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(any(), eq("alice"))).thenReturn("jwt-token");

        AuthResponse response = service.login("alice", "pw");

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUsername()).isEqualTo("alice");
    }
}
