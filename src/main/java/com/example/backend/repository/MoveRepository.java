package com.example.backend.repository;

import com.example.backend.entity.Move;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MoveRepository extends JpaRepository<Move, Long> {

    List<Move> findByGameIdOrderByTurnNumberAscMoveNumberAsc(
            Long gameId
    );

    boolean existsByGameIdAndTurnNumberAndMoveNumber(
            Long gameId,
            Integer turnNumber,
            Integer moveNumber
    );
}