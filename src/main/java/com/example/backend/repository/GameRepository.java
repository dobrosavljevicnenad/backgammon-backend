package com.example.backend.repository;

import com.example.backend.entity.Game;
import com.example.backend.enums.GameStatus;

import java.util.List;
import java.util.Optional;

public interface GameRepository {
    Optional<Game> findByRoomId(Long roomId);

    List<Game> findByStatus(GameStatus status);
}
