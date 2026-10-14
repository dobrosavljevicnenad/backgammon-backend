package com.example.backend.repository;

import com.example.backend.entity.RoomPlayer;

import java.util.List;

public interface RoomPlayerRepository {
    List<RoomPlayer> findByRoomId(Long roomId);

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);
}
