package com.example.backend.dto;

import com.example.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String role;
    private int rating;
    private Instant createdAt;

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getRating(),
                user.getCreatedAt()
        );
    }
}
