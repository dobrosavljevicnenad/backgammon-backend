package com.example.backend.dto.response;

import java.time.Instant;

public record MoveResponse(
        Long id,
        Long gameId,
        Long gamePlayerId,
        int turnNumber,
        int moveNumber,
        short fromPosition,
        short toPosition,
        byte diceValue,
        Instant createdAt
) {}