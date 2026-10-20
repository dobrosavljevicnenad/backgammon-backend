package com.example.backend.controller;

import com.example.backend.dto.request.CreateRoomRequest;
import com.example.backend.dto.response.RoomDetailsResponse;
import com.example.backend.dto.response.RoomResponse;
import com.example.backend.service.CurrentUserService;
import com.example.backend.service.RoomService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final CurrentUserService currentUserService;

    public RoomController(
            RoomService roomService,
            CurrentUserService currentUserService
    ) {
        this.roomService = roomService;
        this.currentUserService = currentUserService;
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

    // GET /api/rooms/current -> 200 with the user's WAITING/PLAYING room, or 204 if none
    @GetMapping("/current")
    public ResponseEntity<RoomResponse> getCurrentRoom() {
        return roomService.getCurrentRoom(currentUserService.getCurrentUserId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // GET /api/rooms/5 - only members, including for CLOSED rooms
    @GetMapping("/{id}")
    public RoomDetailsResponse getRoomById(@PathVariable Long id) {
        return roomService.getRoomById(id, currentUserService.getCurrentUserId());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidRoomId() {
        return Map.of("message", "Room ID must be a positive integer");
    }

    // POST /api/rooms - the logged-in user becomes the host
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(
            @Valid @RequestBody CreateRoomRequest request
    ) {
        return roomService.createRoom(
                request,
                currentUserService.getCurrentUserId()
        );
    }
}
