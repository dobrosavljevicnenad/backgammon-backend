package com.example.backend.dto.response;

import com.example.backend.entity.User;
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
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getWins(),
                user.getLosses(),
                user.getRating(),
                user.getCreatedAt()
        );
    }
}
