package com.example.backend.repository;

import com.example.backend.entity.Game;
import com.example.backend.enums.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {

    Optional<Game> findByRoomId(Long roomId);

    List<Game> findByStatus(GameStatus status);
}