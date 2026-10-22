package com.example.backend.repository;

import com.example.backend.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query(value = """
            SELECT new com.example.backend.repository.LeaderboardPlayerProjection(u.id, u.username, u.rating)
            FROM User u
            WHERE u.active = true
            ORDER BY u.rating DESC, u.id ASC
            """, countQuery = "SELECT COUNT(u) FROM User u WHERE u.active = true")
    Page<LeaderboardPlayerProjection> findLeaderboard(Pageable pageable);

    @Query("""
            SELECT new com.example.backend.repository.LeaderboardPlayerProjection(u.id, u.username, u.rating)
            FROM User u
            WHERE u.id = :userId AND u.active = true
            """)
    Optional<LeaderboardPlayerProjection> findLeaderboardPlayer(@Param("userId") Long userId);

    @Query("""
            SELECT COUNT(u)
            FROM User u
            WHERE u.active = true
              AND (u.rating > :rating OR (u.rating = :rating AND u.id < :userId))
            """)
    long countPlayersAhead(@Param("rating") int rating, @Param("userId") Long userId);

    // Per-user lock for create/join, so one user cannot enter two rooms at once
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}