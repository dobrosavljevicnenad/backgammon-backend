package com.example.backend.service;

import com.example.backend.repository.LeaderboardPlayerProjection;
import com.example.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock
    private UserRepository userRepository;

    private LeaderboardService leaderboardService;

    @BeforeEach
    void setUp() {
        leaderboardService = new LeaderboardService(userRepository);
    }

    @Test
    void returnsGlobalRanksAndPaginationMetadata() {
        var pageable = PageRequest.of(2, 2);
        var players = List.of(
                new LeaderboardPlayerProjection(12L, "fifth", 1100),
                new LeaderboardPlayerProjection(15L, "sixth", 1000)
        );
        when(userRepository.findLeaderboard(pageable)).thenReturn(new PageImpl<>(players, pageable, 7));

        var response = leaderboardService.getLeaderboard(2, 2);

        assertEquals(2, response.page());
        assertEquals(2, response.size());
        assertEquals(7, response.totalElements());
        assertEquals(4, response.totalPages());
        assertEquals(List.of(5L, 6L), response.entries().stream().map(entry -> entry.rank()).toList());
        assertEquals(12L, response.entries().get(0).userId());
        assertEquals("fifth", response.entries().get(0).username());
        assertEquals(1100, response.entries().get(0).rating());
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void emptyLeaderboardHasZeroTotals() {
        var pageable = PageRequest.of(0, 20);
        when(userRepository.findLeaderboard(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var response = leaderboardService.getLeaderboard(0, 20);

        assertTrue(response.entries().isEmpty());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
    }

    @Test
    void pageBeyondEndPreservesActualTotals() {
        var pageable = PageRequest.of(3, 2);
        when(userRepository.findLeaderboard(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 3));

        var response = leaderboardService.getLeaderboard(3, 2);

        assertTrue(response.entries().isEmpty());
        assertEquals(3, response.page());
        assertEquals(3, response.totalElements());
        assertEquals(2, response.totalPages());
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "2147483647,2", "2147483647,100"})
    void invalidPaginationIsRejectedBeforeQuerying(int page, int size) {
        var exception = assertThrows(ResponseStatusException.class,
                () -> leaderboardService.getLeaderboard(page, size));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @CsvSource({"1", "100"})
    void acceptsPageSizeBoundaries(int size) {
        var pageable = PageRequest.of(0, size);
        when(userRepository.findLeaderboard(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertEquals(size, leaderboardService.getLeaderboard(0, size).size());
    }

    @Test
    void ranksUseLongArithmeticEvenNearMaximumJpaOffset() {
        int page = 21_474_836;
        var pageable = PageRequest.of(page, 100);
        var players = IntStream.range(0, 100)
                .mapToObj(i -> new LeaderboardPlayerProjection((long) i, "player_" + i, 1000))
                .toList();
        when(userRepository.findLeaderboard(pageable))
                .thenReturn(new PageImpl<>(players, pageable, 2_147_483_700L));

        var response = leaderboardService.getLeaderboard(page, 100);

        assertEquals(2_147_483_601L, response.entries().get(0).rank());
        assertEquals(2_147_483_700L, response.entries().get(99).rank());
    }

    @Test
    void currentPlayerRankUsesCountQueryNotFullLeaderboard() {
        var player = new LeaderboardPlayerProjection(15L, "current_player", 1200);
        when(userRepository.findLeaderboardPlayer(15L)).thenReturn(Optional.of(player));
        when(userRepository.countPlayersAhead(1200, 15L)).thenReturn(42L);

        var response = leaderboardService.getCurrentPlayer(15L);

        assertEquals(43L, response.rank());
        assertEquals(15L, response.userId());
        assertEquals("current_player", response.username());
        assertEquals(1200, response.rating());
        verify(userRepository).findLeaderboardPlayer(15L);
        verify(userRepository).countPlayersAhead(1200, 15L);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void missingOrInactivePlayerIsNotRanked() {
        when(userRepository.findLeaderboardPlayer(15L)).thenReturn(Optional.empty());

        var exception = assertThrows(ResponseStatusException.class,
                () -> leaderboardService.getCurrentPlayer(15L));

        assertEquals(404, exception.getStatusCode().value());
        assertEquals("User is not ranked", exception.getReason());
        verify(userRepository).findLeaderboardPlayer(15L);
        verifyNoMoreInteractions(userRepository);
    }
}
