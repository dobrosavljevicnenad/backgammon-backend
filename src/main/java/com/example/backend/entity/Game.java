package com.example.backend.entity;

import com.example.backend.enums.GameStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "games",
        indexes = {
                @Index(name = "idx_games_status", columnList = "status"),
                @Index(name = "idx_games_turn_user", columnList = "current_turn_user_id"),
                @Index(name = "idx_games_deadline", columnList = "turn_deadline")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, unique = true)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status = GameStatus.IN_PROGRESS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_turn_user_id")
    private User currentTurnUser;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_state", nullable = false, columnDefinition = "json")
    private String currentState;

    @Column(name = "turn_number", nullable = false)
    private int turnNumber = 1;

    @Column(name = "turn_deadline")
    private Instant turnDeadline;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    // Opening roll: each player rolls one die, the higher one starts.
    // currentTurnUser stays null until the opening roll is resolved.
    @Column(name = "opening_white_die")
    private Integer openingWhiteDie;

    @Column(name = "opening_black_die")
    private Integer openingBlackDie;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    public void prePersist() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}
