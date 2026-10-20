package com.example.backend.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

/**
 * Rolls the two dice for a turn. Doubles (both dice showing the same
 * value) grant four moves of that value instead of two, per standard
 * backgammon rules.
 */
@Service
public class DiceService {

    private final Random random = new SecureRandom();

    public List<Integer> roll() {
        int first = random.nextInt(6) + 1;
        int second = random.nextInt(6) + 1;
        return first == second
                ? List.of(first, first, first, first)
                : List.of(first, second);
    }
}
