package com.example.backend.service;

import com.example.backend.dto.response.RoomPlayerResponse;
import com.example.backend.entity.Room;
import com.example.backend.entity.RoomPlayer;
import com.example.backend.entity.User;
import com.example.backend.enums.RoomStatus;
import com.example.backend.repository.RoomPlayerRepository;
import com.example.backend.repository.RoomRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RoomPlayerService {

    private final RoomPlayerRepository roomPlayerRepository;
    private final RoomRepository roomRepository;
    private final RoomMembershipRules roomMembershipRules;

    public RoomPlayerService(
            RoomPlayerRepository roomPlayerRepository,
            RoomRepository roomRepository,
            RoomMembershipRules roomMembershipRules
    ) {
        this.roomPlayerRepository = roomPlayerRepository;
        this.roomRepository = roomRepository;
        this.roomMembershipRules = roomMembershipRules;
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

        return roomPlayerRepository.findByRoomIdOrderByJoinedAtAscIdAsc(roomId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // JOIN ROOM - the second player joins as a guest
    @Transactional
    public RoomPlayerResponse joinRoom(Long roomId, Long userId) {

        // Lock order: user first, then room (same as everywhere else)
        User user = roomMembershipRules.lockUserForNewRoom(userId);

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Room not found"
                ));

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Room is not waiting for players"
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
        // The host is created together with the room, so a joining player is never host.
        // An empty WAITING room cannot exist, because the room is deleted when the host leaves.
        roomPlayer.setHost(false);

        return toResponse(roomPlayerRepository.save(roomPlayer));
    }

    // LEAVE ROOM (before the game starts)
    // Host leaves  -> room is deleted (PRD 8)
    // Guest leaves -> only the guest is removed, room goes back to 1/2
    @Transactional
    public void leaveRoom(Long roomId, Long userId) {

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Room not found"
                ));

        RoomPlayer player = roomPlayerRepository
                .findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "You are not in this room"
                ));

        if (room.getStatus() != RoomStatus.WAITING) {
            // Leaving an active game (opponent wins) belongs to the game flow
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot leave a room while a game is in progress"
            );
        }

        if (player.isHost()) {
            roomPlayerRepository.deleteAll(
                    roomPlayerRepository.findByRoomIdOrderByJoinedAtAscIdAsc(roomId)
            );
            roomRepository.delete(room);
        } else {
            roomPlayerRepository.delete(player);
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
