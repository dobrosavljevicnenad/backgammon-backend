package com.example.backend.repository;

import com.example.backend.entity.GamePlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GamePlayerRepository extends JpaRepository<GamePlayer, Long> {

    List<GamePlayer> findByGameId(Long gameId);

    Optional<GamePlayer> findByGameIdAndUserId(Long gameId, Long userId);

    boolean existsByGameIdAndUserId(Long gameId, Long userId);
}