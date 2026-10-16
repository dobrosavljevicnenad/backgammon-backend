package com.example.backend.repository;

import com.example.backend.entity.RoomPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, Long> {
    List<RoomPlayer> findByRoomId(Long roomId);

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);

    // One query for many rooms: rows of [roomId, playerCount]
    @Query("""
            SELECT rp.room.id, COUNT(rp)
            FROM RoomPlayer rp
            WHERE rp.room.id IN :roomIds
            GROUP BY rp.room.id
            """)
    List<Object[]> countPlayersByRoomIds(@Param("roomIds") Collection<Long> roomIds);

    Optional<RoomPlayer> findByRoomIdAndUserId(
            Long roomId,
            Long userId
    );

    Optional<RoomPlayer> findByRoomIdAndIsHostTrue(Long roomId);
}
