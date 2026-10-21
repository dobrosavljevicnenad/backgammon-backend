package com.example.backend.controller;

import com.example.backend.dto.request.MakeMoveRequest;
import com.example.backend.dto.response.GameResponse;
import com.example.backend.dto.response.MoveResponse;
import com.example.backend.dto.response.GamePlayerResponse;
import com.example.backend.dto.response.GameResponse;
import com.example.backend.dto.response.LegalMoveResponse;
import com.example.backend.service.CurrentUserService;
import com.example.backend.service.GameService;
import com.example.backend.service.MoveService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;
    private final MoveService moveService;
    private final CurrentUserService currentUserService;

    public GameController(
            GameService gameService,
            MoveService moveService,
            CurrentUserService currentUserService
    ) {
        this.gameService = gameService;
        this.moveService = moveService;
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

    // POST /api/games/1/roll - current turn's player rolls the dice
    @PostMapping("/{gameId}/roll")
    public GameResponse rollDice(
            @PathVariable Long gameId
    ) {
        return moveService.rollDice(
    // GET /api/games/1/moves - all moves of the game, only for its players
    @GetMapping("/{gameId}/moves")
    public List<MoveResponse> getMoves(
            @PathVariable Long gameId
    ) {
        return gameService.getMoves(
                gameId,
                currentUserService.getCurrentUserId()
        );
    }

    // GET /api/games/1/legal-moves - moves available with the current roll
    @GetMapping("/{gameId}/legal-moves")
    public List<LegalMoveResponse> legalMoves(
            @PathVariable Long gameId
    ) {
        return moveService.legalMoves(
                gameId,
                currentUserService.getCurrentUserId()
        );
    }

    // POST /api/games/1/moves - apply one checker move for one die
    @PostMapping("/{gameId}/moves")
    public GameResponse makeMove(
            @PathVariable Long gameId,
            @Valid @RequestBody MakeMoveRequest request
    ) {
        return moveService.makeMove(
                gameId,
                currentUserService.getCurrentUserId(),
                request
        );
    }

}