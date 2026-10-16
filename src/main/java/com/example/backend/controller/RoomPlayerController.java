package com.example.backend.controller;

import com.example.backend.dto.request.JoinRoomRequest;
import com.example.backend.dto.response.RoomPlayerResponse;
import com.example.backend.service.RoomPlayerService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms/{roomId}")
public class RoomPlayerController {

    private final RoomPlayerService roomPlayerService;

    public RoomPlayerController(RoomPlayerService roomPlayerService) {
        this.roomPlayerService = roomPlayerService;
    }

    // GET /api/rooms/1/players
    @GetMapping("/players")
    public List<RoomPlayerResponse> getPlayers(
            @PathVariable Long roomId
    ) {
        return roomPlayerService.getRoomPlayers(roomId);
    }

    // POST /api/rooms/1/join
    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomPlayerResponse joinRoom(
            @PathVariable Long roomId,
            @Valid @RequestBody JoinRoomRequest request
    ) {
        return roomPlayerService.joinRoom(roomId, request.userId());
    }

    // DELETE /api/rooms/1/players/2
    @DeleteMapping("/players/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveRoom(
            @PathVariable Long roomId,
            @PathVariable Long userId
    ) {
        roomPlayerService.leaveRoom(roomId, userId);
    }
}