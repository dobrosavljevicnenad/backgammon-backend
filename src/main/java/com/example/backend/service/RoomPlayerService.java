package com.example.backend.service;

import com.example.backend.dto.response.RoomPlayerResponse;
import com.example.backend.entity.Room;
import com.example.backend.entity.RoomPlayer;
import com.example.backend.entity.User;
import com.example.backend.enums.RoomStatus;
import com.example.backend.repository.RoomPlayerRepository;
import com.example.backend.repository.RoomRepository;
import com.example.backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RoomPlayerService {

    private final RoomPlayerRepository roomPlayerRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public RoomPlayerService(
            RoomPlayerRepository roomPlayerRepository,
            RoomRepository roomRepository,
            UserRepository userRepository
    ) {
        this.roomPlayerRepository = roomPlayerRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    // GET ALL PLAYERS IN A ROOM
    @Transactional(readOnly = true)
    public List<RoomPlayerResponse> getRoomPlayers(Long roomId) {

        if (!roomRepository.existsById(roomId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room not found"
            );
        }

        return roomPlayerRepository.findByRoomId(roomId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // JOIN ROOM
    @Transactional
    public RoomPlayerResponse joinRoom(Long roomId, Long userId) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Room not found"
                ));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"
                ));

        if (!user.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "User is inactive"
            );
        }

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Room is not waiting for players"
            );
        }

        if (roomPlayerRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "User already joined this room"
            );
        }

        long playerCount = roomPlayerRepository.countByRoomId(roomId);

        if (playerCount >= 2) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Room is full"
            );
        }

        RoomPlayer roomPlayer = new RoomPlayer();
        roomPlayer.setRoom(room);
        roomPlayer.setUser(user);
        roomPlayer.setHost(playerCount == 0);

        return toResponse(roomPlayerRepository.save(roomPlayer));
    }

    // LEAVE ROOM
    @Transactional
    public void leaveRoom(Long roomId, Long userId) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Room not found"
                ));

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot leave a room while a game is in progress"
            );
        }

        RoomPlayer player = roomPlayerRepository
                .findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User is not in this room"
                ));

        boolean wasHost = player.isHost();

        roomPlayerRepository.delete(player);
        roomPlayerRepository.flush();

        if (wasHost) {
            roomPlayerRepository.findByRoomId(roomId)
                    .stream()
                    .findFirst()
                    .ifPresent(nextHost -> nextHost.setHost(true));
        }
    }

    // ENTITY -> DTO
    private RoomPlayerResponse toResponse(RoomPlayer player) {

        return new RoomPlayerResponse(
                player.getId(),
                player.getRoom().getId(),
                player.getUser().getId(),
                player.getUser().getUsername(),
                player.isHost(),
                player.getJoinedAt()
        );
    }
}