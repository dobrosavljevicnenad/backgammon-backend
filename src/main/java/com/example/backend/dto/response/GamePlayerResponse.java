package com.example.backend.dto.response;

import com.example.backend.enums.CheckerColor;

public record GamePlayerResponse(
        Long id,
        Long gameId,
        Long userId,
        String username,
        CheckerColor color,
        boolean winner
) {}