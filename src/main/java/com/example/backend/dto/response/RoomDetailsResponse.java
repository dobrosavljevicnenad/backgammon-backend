package com.example.backend.dto.response;

import com.example.backend.enums.RoomStatus;

import java.time.Instant;
import java.util.List;

/**
 * Member-only room details. Player IDs identify memberships; userId identifies the user.
 */
public record RoomDetailsResponse(
        Long id,
        String name,
        RoomStatus status,
        long playerCount,
        int maxPlayers,
        Instant createdAt,
        Long gameId,
        List<RoomPlayerResponse> players
) {}
