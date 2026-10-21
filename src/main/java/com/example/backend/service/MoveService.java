package com.example.backend.service;

import com.example.backend.dto.request.MakeMoveRequest;
import com.example.backend.dto.response.GameResponse;
import com.example.backend.dto.response.LegalMoveResponse;
import com.example.backend.engine.BoardState;
import com.example.backend.engine.CandidateMove;
import com.example.backend.engine.GameEngine;
import com.example.backend.engine.GameState;
import com.example.backend.entity.Game;
import com.example.backend.entity.GamePlayer;
import com.example.backend.entity.Move;
import com.example.backend.enums.CheckerColor;
import com.example.backend.enums.GameStatus;
import com.example.backend.repository.GamePlayerRepository;
import com.example.backend.repository.GameRepository;
import com.example.backend.repository.MoveRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Dice rolling and move validation/application for an in-progress game.
 * Each public method locks the {@link Game} row (via
 * {@link GameRepository#findByIdForUpdate}) so two requests for the same
 * game can't race each other.
 */
@Service
public class MoveService {

    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final MoveRepository moveRepository;
    private final GameService gameService;
    private final DiceService diceService;
    private final GameUpdateBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public MoveService(
            GameRepository gameRepository,
            GamePlayerRepository gamePlayerRepository,
            MoveRepository moveRepository,
            GameService gameService,
            DiceService diceService,
            GameUpdateBroadcaster broadcaster,
            ObjectMapper objectMapper
    ) {
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.moveRepository = moveRepository;
        this.gameService = gameService;
        this.diceService = diceService;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    // POST /api/games/{gameId}/roll - opening roll while no one has the turn yet,
    // otherwise the current turn's player rolls the dice
    @Transactional
    public GameResponse rollDice(Long gameId, Long userId) {

        Game game = loadForUpdate(gameId);
        requireInProgress(game);

        if (game.getCurrentTurnUser() == null) {
            return rollOpeningDie(game, userId);
        }

        requireCurrentTurn(game, userId);

        GameState state = GameState.fromJson(objectMapper, game.getCurrentState());

        if (!state.getDice().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Dice were already rolled this turn"
            );
        }

        CheckerColor color = colorOf(game, userId);
        List<Integer> dice = diceService.roll();
        state.setDice(dice);

        if (!GameEngine.hasAnyLegalMove(state.getBoard(), color, dice)) {
            // No legal move with this roll: forfeit the turn immediately.
            switchTurn(game);
            state.setDice(List.of());
        }

        game.setCurrentState(state.toJson(objectMapper));
        Game saved = gameRepository.save(game);

        GameResponse response = gameService.toResponse(saved);
        broadcaster.broadcast(response);
        return response;
    }

    // Opening roll: each player rolls one die, the higher one starts.
    // A tie clears both dice so the players roll again.
    private GameResponse rollOpeningDie(Game game, Long userId) {
        GamePlayer gamePlayer = gamePlayerRepository.findByGameIdAndUserId(game.getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You are not a player in this game"
                ));

        boolean isWhite = gamePlayer.getColor() == CheckerColor.WHITE;
        Integer alreadyRolled = isWhite ? game.getOpeningWhiteDie() : game.getOpeningBlackDie();
        if (alreadyRolled != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already rolled for the opening"
            );
        }

        int die = diceService.roll().get(0);
        if (isWhite) {
            game.setOpeningWhiteDie(die);
        } else {
            game.setOpeningBlackDie(die);
        }

        if (game.getOpeningWhiteDie() != null && game.getOpeningBlackDie() != null) {
            resolveOpening(game);
        }

        GameResponse response = gameService.toResponse(gameRepository.save(game));
        broadcaster.broadcast(response);
        return response;
    }

    private void resolveOpening(Game game) {
        int white = game.getOpeningWhiteDie();
        int black = game.getOpeningBlackDie();

        if (white == black) {
            game.setOpeningWhiteDie(null);
            game.setOpeningBlackDie(null);
            return;
        }

        CheckerColor starterColor = white > black ? CheckerColor.WHITE : CheckerColor.BLACK;
        GamePlayer starter = gamePlayerRepository.findByGameId(game.getId())
                .stream()
                .filter(p -> p.getColor() == starterColor)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Starting player not found"
                ));

        game.setCurrentTurnUser(starter.getUser());
    }

    // GET /api/games/{gameId}/legal-moves - moves available with the current roll
    @Transactional(readOnly = true)
    public List<LegalMoveResponse> legalMoves(Long gameId, Long userId) {

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Game not found"
                ));

        CheckerColor color = colorOf(game, userId);

        boolean isCurrentTurn = game.getCurrentTurnUser() != null
                && game.getCurrentTurnUser().getId().equals(userId);

        GameState state = GameState.fromJson(objectMapper, game.getCurrentState());

        if (!isCurrentTurn || state.getDice().isEmpty()) {
            return List.of();
        }

        return GameEngine.legalMoves(state.getBoard(), color, state.getDice())
                .stream()
                .map(m -> new LegalMoveResponse(m.from(), m.to(), m.dieValue()))
                .toList();
    }

    // POST /api/games/{gameId}/moves - apply one checker move for one die
    @Transactional
    public GameResponse makeMove(Long gameId, Long userId, MakeMoveRequest request) {

        Game game = loadForUpdate(gameId);
        requireInProgress(game);
        requireCurrentTurn(game, userId);

        GamePlayer gamePlayer = gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You are not a player in this game"
                ));
        CheckerColor color = gamePlayer.getColor();

        GameState state = GameState.fromJson(objectMapper, game.getCurrentState());

        if (state.getDice().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Roll the dice before moving"
            );
        }

        int from = request.fromPosition();
        int to = request.toPosition();
        int dieValue = request.diceValue();

        if (!state.getDice().contains(dieValue)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "That die value is not available this turn"
            );
        }

        if (!GameEngine.isLegalMove(state.getBoard(), color, from, to, dieValue)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Illegal move"
            );
        }

        BoardState newBoard = GameEngine.apply(
                state.getBoard(),
                color,
                new CandidateMove(from, to, dieValue)
        );
        state.setBoard(newBoard);

        List<Integer> remainingDice = new ArrayList<>(state.getDice());
        remainingDice.remove(Integer.valueOf(dieValue));
        state.setDice(remainingDice);

        persistMove(game, gamePlayer, (short) from, (short) to, (byte) dieValue);

        if (newBoard.offCount(color) == 15) {
            game.setStatus(GameStatus.FINISHED);
            game.setFinishedAt(Instant.now());
            gamePlayer.setWinner(true);
            gamePlayerRepository.save(gamePlayer);
            gameService.settleFinishedGame(game, gamePlayer);
        } else if (!GameEngine.hasAnyLegalMove(newBoard, color, remainingDice)) {
            switchTurn(game);
            state.setDice(List.of());
        }

        game.setCurrentState(state.toJson(objectMapper));
        Game saved = gameRepository.save(game);

        GameResponse response = gameService.toResponse(saved);
        broadcaster.broadcast(response);
        return response;
    }

    private void persistMove(Game game, GamePlayer gamePlayer, short from, short to, byte dieValue) {
        int turnNumber = game.getTurnNumber();
        int moveNumber = moveRepository.countByGameIdAndTurnNumber(game.getId(), turnNumber) + 1;

        Move move = new Move();
        move.setGame(game);
        move.setGamePlayer(gamePlayer);
        move.setTurnNumber(turnNumber);
        move.setMoveNumber(moveNumber);
        move.setFromPosition(from);
        move.setToPosition(to);
        move.setDiceValue(dieValue);

        moveRepository.save(move);
    }

    private Game loadForUpdate(Long gameId) {
        return gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Game not found"
                ));
    }

    private void requireInProgress(Game game) {
        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Game is not in progress"
            );
        }
    }

    private void requireCurrentTurn(Game game, Long userId) {
        if (game.getCurrentTurnUser() == null
                || !game.getCurrentTurnUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "It is not your turn"
            );
        }
    }

    private CheckerColor colorOf(Game game, Long userId) {
        return gamePlayerRepository.findByGameIdAndUserId(game.getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You are not a player in this game"
                ))
                .getColor();
    }

    // Hands the turn to the other player and advances the turn counter.
    private void switchTurn(Game game) {
        GamePlayer next = gamePlayerRepository.findByGameId(game.getId())
                .stream()
                .filter(p -> !p.getUser().getId().equals(game.getCurrentTurnUser().getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Other player not found"
                ));

        game.setCurrentTurnUser(next.getUser());
        game.setTurnNumber(game.getTurnNumber() + 1);
    }
}
