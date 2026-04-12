package com.flowboard.controller;

import com.flowboard.dto.AuthResponse;
import com.flowboard.dto.LoginRequest;
import com.flowboard.dto.RegisterRequest;
import com.flowboard.dto.UpdateUserProfileRequest;
import com.flowboard.dto.UserDTO;
import com.flowboard.service.AuthService;
import com.flowboard.service.JWTService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "http://localhost:*")
public class AuthController {
    private final AuthService authService;
    private final JWTService jwtService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
        @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
        @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = jwtService.extractBearerToken(authHeader);
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile")
    public ResponseEntity<UserDTO> getProfile(@RequestHeader("Authorization") String authHeader) {
        UUID userId = jwtService.extractUserIdFromAuthHeader(authHeader);
        return ResponseEntity.ok(authService.getUserProfile(userId.toString()));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserDTO> updateProfile(
        @RequestHeader("Authorization") String authHeader,
        @Valid @RequestBody UpdateUserProfileRequest request) {
        UUID userId = jwtService.extractUserIdFromAuthHeader(authHeader);
        return ResponseEntity.ok(authService.updateUserProfile(userId.toString(), request));
    }
}
