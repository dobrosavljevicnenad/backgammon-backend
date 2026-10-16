package com.example.backend.dto.response;

import java.time.Instant;

public record RoomPlayerResponse(
        Long id,
        Long roomId,
        Long userId,
        String username,
        boolean host,
        Instant joinedAt
) {}