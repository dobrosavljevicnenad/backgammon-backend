package com.example.backend.repository;

import com.example.backend.entity.Game;
import com.example.backend.enums.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {

    Optional<Game> findByRoomId(Long roomId);

    List<Game> findByStatus(GameStatus status);

    // One query for many rooms: rows of [roomId, gameId]
    @Query("""
            SELECT g.room.id, g.id
            FROM Game g
            WHERE g.room.id IN :roomIds
            """)
    List<Object[]> findGameIdsByRoomIds(@Param("roomIds") Collection<Long> roomIds);
}