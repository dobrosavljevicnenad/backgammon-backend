package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "moves",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_moves_game_turn_move",
                        columnNames = {
                                "game_id",
                                "turn_number",
                                "move_number"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_moves_game_player",
                        columnList = "game_player_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Move {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_player_id", nullable = false)
    private GamePlayer gamePlayer;

    @Column(name = "turn_number", nullable = false)
    private int turnNumber;

    @Column(name = "move_number", nullable = false)
    private int moveNumber;

    // 0 = BAR, 1..24 = board points
    @Column(name = "from_position", nullable = false)
    private short fromPosition;

    // 1..24 = board points, 25 = OFF
    @Column(name = "to_position", nullable = false)
    private short toPosition;

    @Column(name = "dice_value", nullable = false)
    private byte diceValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
