package com.example.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EloRatingService {

    private final int kFactor;

    public EloRatingService(@Value("${app.elo.k-factor}") int kFactor) {
        if (kFactor <= 0) {
            throw new IllegalArgumentException("app.elo.k-factor must be a positive integer");
        }
        this.kFactor = kFactor;
    }

    public EloRatingResult calculateRatings(int winnerRating, int loserRating) {
        if (winnerRating < 0 || loserRating < 0) {
            throw new IllegalArgumentException("Winner and loser ratings must be non-negative");
        }

        double expectedWinnerScore = 1.0
                / (1.0 + Math.pow(10.0, ((double) loserRating - winnerRating) / 400.0));
        long ratingChange = Math.round(kFactor * (1.0 - expectedWinnerScore));

        return new EloRatingResult(
                Math.toIntExact(Math.max(0L, winnerRating + ratingChange)),
                Math.toIntExact(Math.max(0L, loserRating - ratingChange))
        );
    }
}
