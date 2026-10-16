package com.example.backend.controller;

import com.example.backend.dto.response.GameResponse;
import com.example.backend.dto.response.GamePlayerResponse;
import com.example.backend.service.GameService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    // POST /api/games/rooms/1/start
    @PostMapping("/rooms/{roomId}/start")
    @ResponseStatus(HttpStatus.CREATED)
    public GameResponse startGame(
            @PathVariable Long roomId
    ) {
        return gameService.startGame(roomId);
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