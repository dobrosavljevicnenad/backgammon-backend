package com.example.backend.service;

import com.example.backend.dto.request.CreateRoomRequest;
import com.example.backend.dto.response.RoomResponse;
import com.example.backend.entity.Room;
import com.example.backend.enums.RoomStatus;
import com.example.backend.repository.RoomPlayerRepository;
import com.example.backend.repository.RoomRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;

    // Constructor Dependency Injection
    public RoomService(
            RoomRepository roomRepository,
            RoomPlayerRepository roomPlayerRepository
    ) {
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
    }

    // GET ALL ROOMS
    @Transactional(readOnly = true)
    public List<RoomResponse> getAllRooms() {
        return toResponses(roomRepository.findAll());
    }

    // GET ROOM BY ID
    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long id) {

        Room room = roomRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Room not found"
                        )
                );

        return toResponse(room);
    }

    // GET WAITING ROOMS
    @Transactional(readOnly = true)
    public List<RoomResponse> getWaitingRooms() {

        return toResponses(
                roomRepository.findByStatus(RoomStatus.WAITING)
        );
    }

    // CREATE ROOM
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {

        Room room = new Room();

        room.setName(request.name().trim());
        room.setStatus(RoomStatus.WAITING);

        Room savedRoom = roomRepository.save(room);

        return toResponse(savedRoom);
    }

    // CONVERT ENTITIES TO DTOs (one count query for all rooms)
    private List<RoomResponse> toResponses(List<Room> rooms) {

        if (rooms.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> playerCounts = new HashMap<>();

        roomPlayerRepository
                .countPlayersByRoomIds(
                        rooms.stream().map(Room::getId).toList()
                )
                .forEach(row -> playerCounts.put(
                        (Long) row[0],
                        (Long) row[1]
                ));

        return rooms.stream()
                .map(room -> toResponse(
                        room,
                        playerCounts.getOrDefault(room.getId(), 0L)
                ))
                .toList();
    }

    // CONVERT ENTITY TO DTO
    private RoomResponse toResponse(Room room) {
        return toResponse(
                room,
                roomPlayerRepository.countByRoomId(room.getId())
        );
    }

    private RoomResponse toResponse(Room room, long playerCount) {

        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getStatus(),
                playerCount,
                room.getCreatedAt()
        );
    }
}