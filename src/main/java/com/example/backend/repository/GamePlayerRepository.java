package com.example.backend.repository;

import com.example.backend.entity.GamePlayer;

import java.util.List;
import java.util.Optional;

public interface GamePlayerRepository {

    List<GamePlayer> findByGameId(Long gameId);

    Optional<GamePlayer> findByGameIdAndUserId(Long gameId, Long userId);

    boolean existsByGameIdAndUserId(Long gameId, Long userId);


}
