package com.example.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(

        @NotBlank(message = "Room name is required")
        @Size(max = 100, message = "Room name must be at most 100 characters")
        String name

) {}