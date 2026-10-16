package com.example.backend.controller;

import com.example.backend.dto.response.RoomPlayerResponse;
import com.example.backend.service.CurrentUserService;
import com.example.backend.service.RoomPlayerService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms/{roomId}")
public class RoomPlayerController {

    private final RoomPlayerService roomPlayerService;
    private final CurrentUserService currentUserService;

    public RoomPlayerController(
            RoomPlayerService roomPlayerService,
            CurrentUserService currentUserService
    ) {
        this.roomPlayerService = roomPlayerService;
        this.currentUserService = currentUserService;
    }

    // GET /api/rooms/1/players
    @GetMapping("/players")
    public List<RoomPlayerResponse> getPlayers(
            @PathVariable Long roomId
    ) {
        return roomPlayerService.getRoomPlayers(roomId);
    }

    // POST /api/rooms/1/join - the logged-in user joins (no body)
    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomPlayerResponse joinRoom(
            @PathVariable Long roomId
    ) {
        return roomPlayerService.joinRoom(
                roomId,
                currentUserService.getCurrentUserId()
        );
    }

    // POST /api/rooms/1/leave - the logged-in user leaves (host leaving deletes the room)
    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveRoom(
            @PathVariable Long roomId
    ) {
        roomPlayerService.leaveRoom(
                roomId,
                currentUserService.getCurrentUserId()
        );
    }
}
