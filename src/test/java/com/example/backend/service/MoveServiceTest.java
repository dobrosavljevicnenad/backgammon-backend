package com.example.backend.service;

import com.example.backend.dto.request.MakeMoveRequest;
import com.example.backend.engine.BoardState;
import com.example.backend.engine.GameState;
import com.example.backend.entity.Game;
import com.example.backend.entity.GamePlayer;
import com.example.backend.entity.Move;
import com.example.backend.entity.Room;
import com.example.backend.entity.User;
import com.example.backend.enums.CheckerColor;
import com.example.backend.enums.GameStatus;
import com.example.backend.repository.GamePlayerRepository;
import com.example.backend.repository.GameRepository;
import com.example.backend.repository.MoveRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoveServiceTest {

    @Mock
    private GameRepository gameRepository;
    @Mock
    private GamePlayerRepository gamePlayerRepository;
    @Mock
    private MoveRepository moveRepository;
    @Mock
    private GameService gameService;
    @Mock
    private DiceService diceService;
    @Mock
    private GameUpdateBroadcaster broadcaster;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InitialGameStateFactory initialGameStateFactory = new InitialGameStateFactory(objectMapper);

    private MoveService moveService;

    private Game game;
    private User whiteUser;
    private User blackUser;
    private GamePlayer whitePlayer;
    private GamePlayer blackPlayer;

    @BeforeEach
    void setUp() {
        moveService = new MoveService(
                gameRepository, gamePlayerRepository, moveRepository, gameService, diceService, broadcaster, objectMapper
        );

        Room room = new Room();
        room.setId(10L);

        whiteUser = new User();
        whiteUser.setId(100L);
        blackUser = new User();
        blackUser.setId(200L);

        game = new Game();
        game.setId(1L);
        game.setRoom(room);
        game.setStatus(GameStatus.IN_PROGRESS);
        game.setTurnNumber(1);
        game.setCurrentTurnUser(whiteUser);
        game.setCurrentState(initialGameStateFactory.create());

        whitePlayer = new GamePlayer();
        whitePlayer.setId(1L);
        whitePlayer.setGame(game);
        whitePlayer.setUser(whiteUser);
        whitePlayer.setColor(CheckerColor.WHITE);

        blackPlayer = new GamePlayer();
        blackPlayer.setId(2L);
        blackPlayer.setGame(game);
        blackPlayer.setUser(blackUser);
        blackPlayer.setColor(CheckerColor.BLACK);
    }

    private void givenState(BoardState board, List<Integer> dice) {
        game.setCurrentState(new GameState(board, dice).toJson(objectMapper));
    }

    private GameState currentState() {
        return GameState.fromJson(objectMapper, game.getCurrentState());
    }

    // ---- rollDice ----

    @Test
    void rollDiceRejectedWhenNotYourTurn() {
        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.rollDice(1L, blackUser.getId()));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void rollDiceRejectedWhenAlreadyRolled() {
        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));
        givenState(BoardState.fromJson(objectMapper, game.getCurrentState()), List.of(3, 5));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.rollDice(1L, whiteUser.getId()));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void rollDiceStoresRollAndKeepsTurnWhenLegalMoveExists() {
        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameIdAndUserId(1L, 100L)).thenReturn(Optional.of(whitePlayer));
        when(diceService.roll()).thenReturn(List.of(3, 5));
        when(gameRepository.save(any())).thenReturn(game);

        moveService.rollDice(1L, whiteUser.getId());

        assertEquals(List.of(3, 5), currentState().getDice());
        assertEquals(whiteUser.getId(), game.getCurrentTurnUser().getId());
        assertEquals(1, game.getTurnNumber());
    }

    @Test
    void rollDiceForfeitsTurnWhenNoLegalMoveExists() {
        // WHITE has a single checker on 24; BLACK occupies every point it could land on.
        int[] points = new int[25];
        points[24] = 1;
        for (int p = 18; p <= 23; p++) {
            points[p] = -2;
        }
        BoardState blocked = new BoardState(points, 0, 0, 0, 0);

        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameIdAndUserId(1L, 100L)).thenReturn(Optional.of(whitePlayer));
        when(gamePlayerRepository.findByGameId(1L)).thenReturn(List.of(whitePlayer, blackPlayer));
        when(diceService.roll()).thenReturn(List.of(2, 4));
        when(gameRepository.save(any())).thenReturn(game);

        givenState(blocked, List.of());

        moveService.rollDice(1L, whiteUser.getId());

        assertTrue(currentState().getDice().isEmpty());
        assertEquals(blackUser.getId(), game.getCurrentTurnUser().getId());
        assertEquals(2, game.getTurnNumber());
    }

    // ---- legalMoves ----

    @Test
    void legalMovesEmptyWhenNotCurrentPlayersTurn() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameIdAndUserId(1L, 200L)).thenReturn(Optional.of(blackPlayer));
        givenState(BoardState.fromJson(objectMapper, game.getCurrentState()), List.of(3, 5));

        assertTrue(moveService.legalMoves(1L, blackUser.getId()).isEmpty());
    }

    @Test
    void legalMovesEmptyWhenDiceNotYetRolled() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameIdAndUserId(1L, 100L)).thenReturn(Optional.of(whitePlayer));

        assertTrue(moveService.legalMoves(1L, whiteUser.getId()).isEmpty());
    }

    @Test
    void legalMovesListsOptionsForCurrentRoll() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameIdAndUserId(1L, 100L)).thenReturn(Optional.of(whitePlayer));
        givenState(BoardState.fromJson(objectMapper, game.getCurrentState()), List.of(3));

        var moves = moveService.legalMoves(1L, whiteUser.getId());

        assertTrue(moves.stream().anyMatch(m -> m.fromPosition() == 24 && m.toPosition() == 21 && m.diceValue() == 3));
    }

    // ---- makeMove ----

    @Test
    void makeMoveRejectedWhenDieNotAvailable() {
        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameIdAndUserId(1L, 100L)).thenReturn(Optional.of(whitePlayer));
        givenState(BoardState.fromJson(objectMapper, game.getCurrentState()), List.of(3, 5));

        MakeMoveRequest request = new MakeMoveRequest((short) 24, (short) 18, (byte) 6);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.makeMove(1L, whiteUser.getId(), request));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void makeMoveRejectedWhenDestinationBlocked() {
        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameIdAndUserId(1L, 100L)).thenReturn(Optional.of(whitePlayer));
        givenState(BoardState.fromJson(objectMapper, game.getCurrentState()), List.of(5));

        // Point 19 has 5 BLACK checkers in the opening position: blocked for WHITE.
        MakeMoveRequest request = new MakeMoveRequest((short) 24, (short) 19, (byte) 5);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.makeMove(1L, whiteUser.getId(), request));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void makeMoveAppliesMoveConsumesDieAndPersistsMoveRow() {
        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameIdAndUserId(1L, 100L)).thenReturn(Optional.of(whitePlayer));
        when(moveRepository.countByGameIdAndTurnNumber(1L, 1)).thenReturn(0);
        when(gameRepository.save(any())).thenReturn(game);
        givenState(BoardState.fromJson(objectMapper, game.getCurrentState()), List.of(3, 5));

        MakeMoveRequest request = new MakeMoveRequest((short) 24, (short) 21, (byte) 3);

        moveService.makeMove(1L, whiteUser.getId(), request);

        ArgumentCaptor<Move> moveCaptor = ArgumentCaptor.forClass(Move.class);
        verify(moveRepository).save(moveCaptor.capture());
        Move saved = moveCaptor.getValue();
        assertEquals(1, saved.getTurnNumber());
        assertEquals(1, saved.getMoveNumber());
        assertEquals((short) 24, saved.getFromPosition());
        assertEquals((short) 21, saved.getToPosition());
        assertEquals((byte) 3, saved.getDiceValue());

        GameState result = currentState();
        assertEquals(List.of(5), result.getDice());
        assertEquals(1, result.getBoard().at(21));
        assertEquals(1, result.getBoard().at(24));
        // Turn stays with WHITE: die 5 still has a legal move (13 -> 8).
        assertEquals(whiteUser.getId(), game.getCurrentTurnUser().getId());
    }

    @Test
    void makeMoveSwitchesTurnWhenNoLegalMovesRemainWithLeftoverDice() {
        int[] points = new int[25];
        points[24] = 1;
        for (int p = 19; p <= 23; p++) {
            points[p] = -2; // blocks dice 1-5 from point 24
        }
        points[15] = -2; // blocks die 3 after moving 24 -> 18 with die 6
        BoardState board = new BoardState(points, 0, 0, 0, 0);
        givenState(board, List.of(6, 3));

        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameIdAndUserId(1L, 100L)).thenReturn(Optional.of(whitePlayer));
        when(gamePlayerRepository.findByGameId(1L)).thenReturn(List.of(whitePlayer, blackPlayer));
        when(moveRepository.countByGameIdAndTurnNumber(1L, 1)).thenReturn(0);
        when(gameRepository.save(any())).thenReturn(game);

        MakeMoveRequest request = new MakeMoveRequest((short) 24, (short) 18, (byte) 6);

        moveService.makeMove(1L, whiteUser.getId(), request);

        GameState result = currentState();
        assertTrue(result.getDice().isEmpty());
        assertEquals(blackUser.getId(), game.getCurrentTurnUser().getId());
        assertEquals(2, game.getTurnNumber());
    }

    @Test
    void makeMoveFinishesGameWhenLastCheckerBearsOff() {
        int[] points = new int[25];
        points[3] = 1; // last WHITE checker, rest already off
        BoardState board = new BoardState(points, 0, 0, 14, 0);
        givenState(board, List.of(3));

        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.findByGameIdAndUserId(1L, 100L)).thenReturn(Optional.of(whitePlayer));
        when(moveRepository.countByGameIdAndTurnNumber(1L, 1)).thenReturn(0);
        when(gameRepository.save(any())).thenReturn(game);

        MakeMoveRequest request = new MakeMoveRequest((short) 3, (short) 25, (byte) 3);

        moveService.makeMove(1L, whiteUser.getId(), request);

        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertTrue(whitePlayer.isWinner());
        assertEquals(15, currentState().getBoard().offCount(CheckerColor.WHITE));
        assertFalse(currentState().getBoard().at(3) > 0);
    }
}
