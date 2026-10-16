package com.example.backend.repository;

import com.example.backend.entity.Room;
import com.example.backend.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import java.util.List;


public interface RoomRepository extends JpaRepository<Room, Long> {
    // vraca sobe koje cekaju igrace
    List<Room> findByStatus(RoomStatus status);

    List<Room> findByStatusOrderByCreatedAtDesc(RoomStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :roomId")
    Optional<Room> findByIdForUpdate(@Param("roomId") Long roomId);
}
