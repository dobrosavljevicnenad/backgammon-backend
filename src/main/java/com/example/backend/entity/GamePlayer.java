package com.example.backend.entity;

import com.example.backend.enums.CheckerColor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "game_players",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"game_id", "user_id"}
                ),
                @UniqueConstraint(
                        columnNames = {"game_id", "color"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_game_players_user",
                        columnList = "user_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class GamePlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckerColor color;

    @Column(name = "is_winner", nullable = false)
    private boolean isWinner = false;
}
