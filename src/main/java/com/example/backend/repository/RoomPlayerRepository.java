package com.example.backend.repository;

import com.example.backend.entity.RoomPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, Long> {
    List<RoomPlayer> findByRoomId(Long roomId);

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);
}
