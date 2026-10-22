package com.example.backend.dto.response;

import java.util.List;

public record LeaderboardResponse(
        List<LeaderboardEntryResponse> entries,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
