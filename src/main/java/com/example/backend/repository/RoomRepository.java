package com.example.backend.repository;

import com.example.backend.entity.Room;
import com.example.backend.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface RoomRepository extends JpaRepository<Room, Long> {
    // vraca sobe koje cekaju igrace
    List<Room> findByStatus(RoomStatus status);

    List<Room> findByStatusOrderByCreatedAtDesc(RoomStatus status);}
