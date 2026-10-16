package com.example.backend.dto;

import lombok.Data;

@Data
public class AuthResponse {

    private final String token;
    private final String tokenType = "Bearer";
    private final String username;
    private final String email;
    private final String role;

    public AuthResponse(String token, String username, String email, String role) {
        this.token = token;
        this.username = username;
        this.email = email;
        this.role = role;
    }
}
