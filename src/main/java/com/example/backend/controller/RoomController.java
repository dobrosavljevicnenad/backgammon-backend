package com.example.backend.controller;

import com.example.backend.dto.request.CreateRoomRequest;
import com.example.backend.dto.response.RoomResponse;
import com.example.backend.service.RoomService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    // GET /api/rooms
    @GetMapping
    public List<RoomResponse> getAllRooms() {
        return roomService.getAllRooms();
    }

    // GET /api/rooms/waiting
    @GetMapping("/waiting")
    public List<RoomResponse> getWaitingRooms() {
        return roomService.getWaitingRooms();
    }

    // GET /api/rooms/5
    @GetMapping("/{id}")
    public RoomResponse getRoomById(@PathVariable Long id) {
        return roomService.getRoomById(id);
    }

    // POST /api/rooms
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(
            @Valid @RequestBody CreateRoomRequest request
    ) {
        return roomService.createRoom(request);
    }
}