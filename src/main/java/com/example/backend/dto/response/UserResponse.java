package com.example.backend.dto.response;

import com.example.backend.enums.Role;
import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        Role role,
        boolean active,
        int wins,
        int losses,
        int rating,
        Instant createdAt
) {}