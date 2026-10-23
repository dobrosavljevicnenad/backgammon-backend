package com.example.backend.service;

import com.example.backend.entity.Game;
import com.example.backend.entity.GamePlayer;
import com.example.backend.entity.Room;
import com.example.backend.entity.User;
import com.example.backend.enums.GameStatus;
import com.example.backend.enums.RoomStatus;
import com.example.backend.repository.GamePlayerRepository;
import com.example.backend.repository.GameRepository;
import com.example.backend.repository.MoveRepository;
import com.example.backend.repository.RoomPlayerRepository;
import com.example.backend.repository.RoomRepository;
import com.example.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;
    @Mock
    private GamePlayerRepository gamePlayerRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomPlayerRepository roomPlayerRepository;
    @Mock
    private GameUpdateBroadcaster broadcaster;
    @Mock
    private MoveRepository moveRepository;
    @Mock
    private UserRepository userRepository;

    private GameService gameService;
    private Game game;
    private Room room;
    private User firstUser;
    private User secondUser;
    private GamePlayer firstPlayer;
    private GamePlayer secondPlayer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        InitialGameStateFactory initialGameStateFactory = new InitialGameStateFactory(objectMapper);
        gameService = new GameService(
                gameRepository, gamePlayerRepository, roomRepository, roomPlayerRepository,
                initialGameStateFactory, broadcaster, objectMapper, moveRepository,
                new EloRatingService(40), userRepository
        );

        room = new Room();
        room.setId(10L);
        room.setStatus(RoomStatus.PLAYING);

        firstUser = new User();
        firstUser.setId(100L);
        secondUser = new User();
        secondUser.setId(200L);

        game = new Game();
        game.setId(1L);
        game.setRoom(room);
        game.setStatus(GameStatus.IN_PROGRESS);
        game.setCurrentState(initialGameStateFactory.create());

        firstPlayer = new GamePlayer();
        firstPlayer.setGame(game);
        firstPlayer.setUser(firstUser);
        secondPlayer = new GamePlayer();
        secondPlayer.setGame(game);
        secondPlayer.setUser(secondUser);
    }

    @ParameterizedTest
    @CsvSource({"0,0,0,0", "7,3,5,9"})
    void settlementIncrementsOnlyWinnerWinsAndLoserLossesAlongsideRatings(
            int winnerWins, int winnerLosses, int loserWins, int loserLosses
    ) {
        firstUser.setWins(winnerWins);
        firstUser.setLosses(winnerLosses);
        secondUser.setWins(loserWins);
        secondUser.setLosses(loserLosses);
        game.setStatus(GameStatus.FINISHED);
        firstPlayer.setWinner(true);
        when(gamePlayerRepository.findByGameId(1L)).thenReturn(List.of(firstPlayer, secondPlayer));

        gameService.settleFinishedGame(game, firstPlayer);

        assertEquals(winnerWins + 1, firstUser.getWins());
        assertEquals(winnerLosses, firstUser.getLosses());
        assertEquals(loserWins, secondUser.getWins());
        assertEquals(loserLosses + 1, secondUser.getLosses());
        assertEquals(1020, firstUser.getRating());
        assertEquals(980, secondUser.getRating());
        assertEquals(RoomStatus.CLOSED, room.getStatus());
        verify(userRepository).save(firstUser);
        verify(userRepository).save(secondUser);
        verifyNoMoreInteractions(userRepository);
        verify(roomRepository).save(room);
    }

    @Test
    void settlementCountsResultEvenWhenRoundedRatingChangeIsZero() {
        firstUser.setRating(3000);
        secondUser.setRating(0);
        game.setStatus(GameStatus.FINISHED);
        firstPlayer.setWinner(true);
        when(gamePlayerRepository.findByGameId(1L)).thenReturn(List.of(firstPlayer, secondPlayer));

        gameService.settleFinishedGame(game, firstPlayer);

        assertEquals(1, firstUser.getWins());
        assertEquals(0, firstUser.getLosses());
        assertEquals(0, secondUser.getWins());
        assertEquals(1, secondUser.getLosses());
        assertEquals(3000, firstUser.getRating());
        assertEquals(0, secondUser.getRating());
        verify(userRepository).save(firstUser);
        verify(userRepository).save(secondUser);
    }

    @ParameterizedTest
    @ValueSource(longs = {100L, 200L})
    void resignationAwardsOpponentWinAndResigningPlayerLoss(Long resigningUserId) {
        GamePlayer loser = resigningUserId.equals(firstUser.getId()) ? firstPlayer : secondPlayer;
        GamePlayer winner = loser == firstPlayer ? secondPlayer : firstPlayer;
        winner.getUser().setWins(7);
        winner.getUser().setLosses(3);
        loser.getUser().setWins(5);
        loser.getUser().setLosses(9);
        givenFinishRequest(resigningUserId);

        var response = gameService.finishGame(1L, resigningUserId);

        assertEquals(GameStatus.FINISHED, response.status());
        assertNotNull(game.getFinishedAt());
        assertTrue(winner.isWinner());
        assertEquals(RoomStatus.CLOSED, room.getStatus());
        assertEquals(8, winner.getUser().getWins());
        assertEquals(3, winner.getUser().getLosses());
        assertEquals(5, loser.getUser().getWins());
        assertEquals(10, loser.getUser().getLosses());
        assertEquals(1020, winner.getUser().getRating());
        assertEquals(980, loser.getUser().getRating());
        verify(userRepository).save(winner.getUser());
        verify(userRepository).save(loser.getUser());
        verify(gameRepository).save(game);
    }

    @Test
    void repeatedFinishDoesNotApplyCountersOrRatingsTwice() {
        givenFinishRequest(firstUser.getId());
        gameService.finishGame(1L, firstUser.getId());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.finishGame(1L, firstUser.getId()));

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(0, firstUser.getWins());
        assertEquals(1, firstUser.getLosses());
        assertEquals(1, secondUser.getWins());
        assertEquals(0, secondUser.getLosses());
        assertEquals(980, firstUser.getRating());
        assertEquals(1020, secondUser.getRating());
        verify(userRepository).save(firstUser);
        verify(userRepository).save(secondUser);
        verifyNoMoreInteractions(userRepository);
        verify(roomRepository).save(room);
        verify(gameRepository).save(game);
    }

    @Test
    void nonPlayerCannotFinishGameOrChangeStatistics() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> gameService.finishGame(1L, 300L));

        assertEquals(403, exception.getStatusCode().value());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertEquals(0, firstUser.getWins());
        assertEquals(0, firstUser.getLosses());
        assertEquals(0, secondUser.getWins());
        assertEquals(0, secondUser.getLosses());
        assertEquals(1000, firstUser.getRating());
        assertEquals(1000, secondUser.getRating());
        verifyNoInteractions(userRepository, roomRepository);
        verify(gameRepository).findById(1L);
        verifyNoMoreInteractions(gameRepository);
    }

    private void givenFinishRequest(Long resigningUserId) {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gamePlayerRepository.existsByGameIdAndUserId(1L, resigningUserId)).thenReturn(true);
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(gamePlayerRepository.findByGameId(1L)).thenReturn(List.of(firstPlayer, secondPlayer));
        when(gameRepository.save(game)).thenReturn(game);
    }
}
