package com.example.backend.dto.response;

import com.example.backend.enums.RoomStatus;
import java.time.Instant;

public record RoomResponse(
        Long id,
        String name,
        RoomStatus status,
        long playerCount,
        Instant createdAt,
        Long gameId
) {}