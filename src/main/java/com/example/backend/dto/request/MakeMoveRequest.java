package com.example.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MakeMoveRequest(

        @NotNull
        @Min(0)
        @Max(24)
        Short fromPosition,

        @NotNull
        @Min(1)
        @Max(25)
        Short toPosition,

        @NotNull
        @Min(1)
        @Max(6)
        Byte diceValue

) {}