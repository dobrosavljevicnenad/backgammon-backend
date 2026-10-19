package com.example.backend.controller;

import com.example.backend.dto.response.GameResponse;
import com.example.backend.dto.response.GamePlayerResponse;
import com.example.backend.service.CurrentUserService;
import com.example.backend.service.GameService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;
    private final CurrentUserService currentUserService;

    public GameController(
            GameService gameService,
            CurrentUserService currentUserService
    ) {
        this.gameService = gameService;
        this.currentUserService = currentUserService;
    }

    // POST /api/games/rooms/1/start - only the host of the room
    @PostMapping("/rooms/{roomId}/start")
    @ResponseStatus(HttpStatus.CREATED)
    public GameResponse startGame(
            @PathVariable Long roomId
    ) {
        return gameService.startGame(
                roomId,
                currentUserService.getCurrentUserId()
        );
    }

    // POST /api/games/1/finish - a player gives up, game is ABANDONED and room is CLOSED
    @PostMapping("/{gameId}/finish")
    public GameResponse finishGame(
            @PathVariable Long gameId
    ) {
        return gameService.finishGame(
                gameId,
                currentUserService.getCurrentUserId()
        );
    }

    // GET /api/games/1
    @GetMapping("/{gameId}")
    public GameResponse getGameById(
            @PathVariable Long gameId
    ) {
        return gameService.getGameById(gameId);
    }

    // GET /api/games/1/players
    @GetMapping("/{gameId}/players")
    public List<GamePlayerResponse> getGamePlayers(
            @PathVariable Long gameId
    ) {
        return gameService.getGamePlayers(gameId);
    }
}