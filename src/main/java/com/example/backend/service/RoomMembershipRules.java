package com.example.backend.service;

import com.example.backend.entity.User;
import com.example.backend.enums.RoomStatus;
import com.example.backend.repository.RoomPlayerRepository;
import com.example.backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Shared rule for Create Room and Join Room (PRD 9, DATABASE.md 10):
 * a user can be in only one WAITING/PLAYING room at a time.
 */
@Component
public class RoomMembershipRules {

    public static final List<RoomStatus> ACTIVE_ROOM_STATUSES =
            List.of(RoomStatus.WAITING, RoomStatus.PLAYING);

    private final UserRepository userRepository;
    private final RoomPlayerRepository roomPlayerRepository;

    public RoomMembershipRules(
            UserRepository userRepository,
            RoomPlayerRepository roomPlayerRepository
    ) {
        this.userRepository = userRepository;
        this.roomPlayerRepository = roomPlayerRepository;
    }

    /**
     * Locks the user row (SELECT ... FOR UPDATE) and checks that the user
     * may enter a new room. Must be called inside a @Transactional method,
     * before the room is locked (lock order: user -> room).
     */
    public User lockUserForNewRoom(Long userId) {

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"
                ));

        if (!user.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "User is inactive"
            );
        }

        if (roomPlayerRepository.existsByUserIdAndRoomStatusIn(userId, ACTIVE_ROOM_STATUSES)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "You are already in an active room"
            );
        }

        return user;
    }
}
