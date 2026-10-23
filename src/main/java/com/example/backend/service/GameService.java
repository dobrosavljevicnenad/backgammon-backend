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
import com.example.backend.repository.UserRepository;
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
    private final EloRatingService eloRatingService;
    private final UserRepository userRepository;
    private final Random random = new SecureRandom();

    public GameService(
            GameRepository gameRepository,
            GamePlayerRepository gamePlayerRepository,
            RoomRepository roomRepository,
            RoomPlayerRepository roomPlayerRepository,
            InitialGameStateFactory initialGameStateFactory,
            GameUpdateBroadcaster broadcaster,
            ObjectMapper objectMapper,
            MoveRepository moveRepository,
            EloRatingService eloRatingService,
            UserRepository userRepository
    ) {
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.initialGameStateFactory = initialGameStateFactory;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
        this.moveRepository = moveRepository;
        this.eloRatingService = eloRatingService;
        this.userRepository = userRepository;
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

        // First player is decided by the opening roll (MoveService.rollDice)
        game.setCurrentTurnUser(null);

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

    // FINISH GAME - a player gives up. The opponent wins, game becomes FINISHED,
    // wins/losses and Elo are applied and the room becomes CLOSED.
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
        roomRepository.findByIdForUpdate(game.getRoom().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Room not found"
                ));

        GamePlayer opponent = gamePlayerRepository.findByGameId(gameId)
                .stream()
                .filter(p -> !p.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Opponent not found"
                ));

        opponent.setWinner(true);
        gamePlayerRepository.save(opponent);
        game.setStatus(GameStatus.FINISHED);
        game.setFinishedAt(Instant.now());
        settleFinishedGame(game, opponent);

        return toResponse(gameRepository.save(game));
    }

    // Applies the final result: wins/losses and Elo for both players, room becomes CLOSED.
    // Called once, when the game changes to FINISHED.
    @Transactional
    public void settleFinishedGame(Game game, GamePlayer winner) {
        GamePlayer loser = gamePlayerRepository.findByGameId(game.getId())
                .stream()
                .filter(p -> !p.getUser().getId().equals(winner.getUser().getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Loser not found"
                ));

        EloRatingResult ratings = eloRatingService.calculateRatings(
                winner.getUser().getRating(),
                loser.getUser().getRating()
        );
        winner.getUser().setRating(ratings.winnerRating());
        loser.getUser().setRating(ratings.loserRating());
        winner.getUser().setWins(winner.getUser().getWins() + 1);
        loser.getUser().setLosses(loser.getUser().getLosses() + 1);
        userRepository.save(winner.getUser());
        userRepository.save(loser.getUser());

        Room room = game.getRoom();
        room.setStatus(RoomStatus.CLOSED);
        roomRepository.save(room);
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

    // ENTITY -> DTO (package-private: reused by MoveService)
    GameResponse toResponse(Game game) {

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
                game.getFinishedAt(),
                game.getOpeningWhiteDie(),
                game.getOpeningBlackDie()
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