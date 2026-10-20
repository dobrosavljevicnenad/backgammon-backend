package com.example.backend.dto.response;

/** One legal move a player may currently make: see {@code CandidateMove}. */
public record LegalMoveResponse(
        int fromPosition,
        int toPosition,
        int diceValue
) {}
