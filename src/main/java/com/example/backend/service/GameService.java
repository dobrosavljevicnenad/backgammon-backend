package com.example.backend.service;

import com.example.backend.dto.response.GameResponse;
import com.example.backend.dto.response.GamePlayerResponse;
import com.example.backend.entity.Game;
import com.example.backend.entity.GamePlayer;
import com.example.backend.entity.Room;
import com.example.backend.entity.RoomPlayer;
import com.example.backend.enums.CheckerColor;
import com.example.backend.enums.GameStatus;
import com.example.backend.enums.RoomStatus;
import com.example.backend.repository.GamePlayerRepository;
import com.example.backend.repository.GameRepository;
import com.example.backend.repository.RoomPlayerRepository;
import com.example.backend.repository.RoomRepository;
import com.example.backend.dto.response.MoveResponse;
import com.example.backend.entity.Move;
import com.example.backend.repository.MoveRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class GameService {

    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final InitialGameStateFactory initialGameStateFactory;
    private final GameUpdateBroadcaster broadcaster;
    private final MoveRepository moveRepository;
    private final ObjectMapper objectMapper;
    private final Random random = new SecureRandom();

    public GameService(
            GameRepository gameRepository,
            GamePlayerRepository gamePlayerRepository,
            RoomRepository roomRepository,
            RoomPlayerRepository roomPlayerRepository,
            InitialGameStateFactory initialGameStateFactory,
            GameUpdateBroadcaster broadcaster,
            ObjectMapper objectMapper
            ObjectMapper objectMapper,
            MoveRepository moveRepository
    ) {
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.initialGameStateFactory = initialGameStateFactory;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
        this.moveRepository = moveRepository;
    }

    // START GAME - only the host, only with exactly two players
    @Transactional
    public GameResponse startGame(Long roomId, Long userId) {

        // Lock room to prevent concurrent modifications
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Room not found"
                ));

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Room is not waiting"
            );
        }

        List<RoomPlayer> players =
                roomPlayerRepository.findByRoomIdOrderByJoinedAtAscIdAsc(roomId);

        boolean requesterIsHost = players.stream()
                .anyMatch(p -> p.isHost() && p.getUser().getId().equals(userId));

        if (!requesterIsHost) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the host can start the game"
            );
        }

        if (players.size() != 2) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Exactly two players are required"
            );
        }

        if (gameRepository.findByRoomId(roomId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Game already exists for this room"
            );
        }

        // Random WHITE / BLACK assignment (PRD 10)
        List<RoomPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, random);
        RoomPlayer white = shuffled.get(0);
        RoomPlayer black = shuffled.get(1);

        // Create game
        Game game = new Game();
        game.setRoom(room);
        game.setStatus(GameStatus.IN_PROGRESS);
        game.setTurnNumber(1);
        game.setCurrentState(initialGameStateFactory.create());

        // Temporary rule: WHITE starts first.
        // TODO: replace with the standard opening roll + turnDeadline (game engine step)
        game.setCurrentTurnUser(white.getUser());

        Game savedGame = gameRepository.save(game);

        // Create WHITE player
        GamePlayer whitePlayer = new GamePlayer();
        whitePlayer.setGame(savedGame);
        whitePlayer.setUser(white.getUser());
        whitePlayer.setColor(CheckerColor.WHITE);
        whitePlayer.setWinner(false);

        gamePlayerRepository.save(whitePlayer);

        // Create BLACK player
        GamePlayer blackPlayer = new GamePlayer();
        blackPlayer.setGame(savedGame);
        blackPlayer.setUser(black.getUser());
        blackPlayer.setColor(CheckerColor.BLACK);
        blackPlayer.setWinner(false);

        gamePlayerRepository.save(blackPlayer);

        // Update room status
        room.setStatus(RoomStatus.PLAYING);
        roomRepository.save(room);

        GameResponse response = toResponse(savedGame);
        broadcaster.broadcast(response);
        return response;
    }

    // FINISH GAME - a player gives up. Game becomes ABANDONED, room becomes CLOSED,
    // so both players can create or join a new room afterwards.
    // TODO: game engine step - replace with the real end-of-game flow (winner, FINISHED)
    @Transactional
    public GameResponse finishGame(Long gameId, Long userId) {

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Game not found"
                ));

        if (!gamePlayerRepository.existsByGameIdAndUserId(gameId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not a player in this game"
            );
        }

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Game is not in progress"
            );
        }

        // Same lock as startGame, so room and game status change together
        Room room = roomRepository.findByIdForUpdate(game.getRoom().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Room not found"
                ));

        game.setStatus(GameStatus.ABANDONED);
        game.setFinishedAt(Instant.now());
        room.setStatus(RoomStatus.CLOSED);

        roomRepository.save(room);

        return toResponse(gameRepository.save(game));
    }

    // GET GAME BY ID
    @Transactional(readOnly = true)
    public GameResponse getGameById(Long gameId) {

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Game not found"
                ));

        return toResponse(game);
    }

    // GET GAME PLAYERS
    @Transactional(readOnly = true)
    public List<GamePlayerResponse> getGamePlayers(Long gameId) {

        if (!gameRepository.existsById(gameId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Game not found"
            );
        }

        return gamePlayerRepository.findByGameId(gameId)
                .stream()
                .map(this::toPlayerResponse)
                .toList();
    }

    // ENTITY -> DTO (package-private: reused by MoveService)
    GameResponse toResponse(Game game) {
    // GET MOVES OF A GAME - only players of that game can see them
    @Transactional(readOnly = true)
    public List<MoveResponse> getMoves(Long gameId, Long userId) {

        if (!gameRepository.existsById(gameId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Game not found"
            );
        }

        if (!gamePlayerRepository.existsByGameIdAndUserId(gameId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not a player in this game"
            );
        }

        return moveRepository.findByGameIdOrderByTurnNumberAscMoveNumberAsc(gameId)
                .stream()
                .map(this::toMoveResponse)
                .toList();
    }

    // ENTITY -> DTO
    private MoveResponse toMoveResponse(Move move) {
        return new MoveResponse(
                move.getId(),
                move.getGame().getId(),
                move.getGamePlayer().getId(),
                move.getTurnNumber(),
                move.getMoveNumber(),
                move.getFromPosition(),
                move.getToPosition(),
                move.getDiceValue(),
                move.getCreatedAt()
        );
    }

    // ENTITY -> DTO
    private GameResponse toResponse(Game game) {

        Long currentTurnUserId =
                game.getCurrentTurnUser() == null
                        ? null
                        : game.getCurrentTurnUser().getId();

        return new GameResponse(
                game.getId(),
                game.getRoom().getId(),
                game.getStatus(),
                currentTurnUserId,
                parseState(game.getCurrentState()),
                game.getTurnNumber(),
                game.getTurnDeadline(),
                game.getStartedAt(),
                game.getFinishedAt()
        );
    }

    // Stored as a JSON string in MySQL, returned as a real JSON object
    private JsonNode parseState(String state) {
        return objectMapper.readTree(state);
    }

    private GamePlayerResponse toPlayerResponse(GamePlayer player) {

        return new GamePlayerResponse(
                player.getId(),
                player.getGame().getId(),
                player.getUser().getId(),
                player.getUser().getUsername(),
                player.getColor(),
                player.isWinner()
        );
    }
}