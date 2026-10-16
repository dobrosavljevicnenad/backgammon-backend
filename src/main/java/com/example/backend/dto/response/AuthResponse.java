package com.example.backend.dto.response;

public record AuthResponse(
        String token,
        String tokenType,
        String username,
        String email,
        String role
) {
    public AuthResponse(String token, String username, String email, String role) {
        this(token, "Bearer", username, email, role);
    }
}
