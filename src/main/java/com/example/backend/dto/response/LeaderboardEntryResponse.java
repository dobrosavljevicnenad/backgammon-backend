package com.example.backend.dto.response;

public record LeaderboardEntryResponse(long rank, Long userId, String username, int rating) {
}
