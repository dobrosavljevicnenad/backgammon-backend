package com.example.backend.service;

import com.example.backend.dto.response.LeaderboardEntryResponse;
import com.example.backend.dto.response.LeaderboardResponse;
import com.example.backend.repository.LeaderboardPlayerProjection;
import com.example.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class LeaderboardService {

    private final UserRepository userRepository;

    public LeaderboardService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LeaderboardResponse getLeaderboard(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        }

        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested page offset is too large");
        }

        Page<LeaderboardPlayerProjection> players = userRepository.findLeaderboard(PageRequest.of(page, size));
        List<LeaderboardEntryResponse> entries = new ArrayList<>(players.getNumberOfElements());
        for (LeaderboardPlayerProjection player : players.getContent()) {
            entries.add(toEntry(player, offset + entries.size() + 1));
        }

        return new LeaderboardResponse(
                List.copyOf(entries), page, size, players.getTotalElements(), players.getTotalPages()
        );
    }

    public LeaderboardEntryResponse getCurrentPlayer(Long userId) {
        LeaderboardPlayerProjection player = userRepository.findLeaderboardPlayer(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not ranked"));

        long rank = userRepository.countPlayersAhead(player.rating(), player.userId()) + 1;
        return toEntry(player, rank);
    }

    private LeaderboardEntryResponse toEntry(LeaderboardPlayerProjection player, long rank) {
        return new LeaderboardEntryResponse(rank, player.userId(), player.username(), player.rating());
    }
}
