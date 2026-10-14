package com.example.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record JoinRoomRequest(
        @NotNull @Positive Long userId
) {}