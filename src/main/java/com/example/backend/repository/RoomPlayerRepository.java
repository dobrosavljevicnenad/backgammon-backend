package com.example.backend.repository;

import com.example.backend.entity.RoomPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, Long> {
    List<RoomPlayer> findByRoomId(Long roomId);

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);

    Optional<RoomPlayer> findByRoomIdAndUserId(
            Long roomId,
            Long userId
    );

    Optional<RoomPlayer> findByRoomIdAndIsHostTrue(Long roomId);
}
