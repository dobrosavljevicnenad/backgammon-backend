package com.example.backend.repository;

import com.example.backend.entity.RoomPlayer;
import com.example.backend.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, Long> {

    // Ordered by join time, so the host (first player) comes first
    List<RoomPlayer> findByRoomIdOrderByJoinedAtAscIdAsc(Long roomId);

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

    // "Is this user already in a WAITING/PLAYING room?"
    boolean existsByUserIdAndRoomStatusIn(Long userId, Collection<RoomStatus> statuses);

    Optional<RoomPlayer> findFirstByUserIdAndRoomStatusIn(Long userId, Collection<RoomStatus> statuses);
}
