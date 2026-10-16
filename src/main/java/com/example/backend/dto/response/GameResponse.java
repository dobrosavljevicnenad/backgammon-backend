package com.example.backend.dto.response;

import com.example.backend.enums.GameStatus;
import java.time.Instant;

public record GameResponse(
        Long id,
        Long roomId,
        GameStatus status,
        Long currentTurnUserId,
        String currentState,
        int turnNumber,
        Instant turnDeadline,
        Instant startedAt,
        Instant finishedAt
) {}