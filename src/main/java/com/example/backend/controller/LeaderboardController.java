package com.example.backend.controller;

import com.example.backend.dto.response.LeaderboardEntryResponse;
import com.example.backend.dto.response.LeaderboardResponse;
import com.example.backend.service.CurrentUserService;
import com.example.backend.service.LeaderboardService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final CurrentUserService currentUserService;

    public LeaderboardController(LeaderboardService leaderboardService, CurrentUserService currentUserService) {
        this.leaderboardService = leaderboardService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<LeaderboardResponse> getLeaderboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(leaderboardService.getLeaderboard(page, size));
    }

    @GetMapping("/me")
    public ResponseEntity<LeaderboardEntryResponse> getCurrentPlayer() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(leaderboardService.getCurrentPlayer(currentUserService.getCurrentUserId()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidParameter(MethodArgumentTypeMismatchException ex) {
        return Map.of("message", ex.getName() + " must be a whole number within the supported integer range");
    }
}
