package com.example.backend.service;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class InitialGameStateFactory {

    private final ObjectMapper objectMapper;

    public InitialGameStateFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String create() {

        // Positive values = WHITE checkers
        // Negative values = BLACK checkers
        // Array index 0 is unused
        int[] points = new int[25];

        // Initial backgammon board setup
        points[1] = -2;
        points[6] = 5;
        points[8] = 3;
        points[12] = -5;

        points[13] = 5;
        points[17] = -3;
        points[19] = -5;
        points[24] = 2;

        Map<String, Object> state = new LinkedHashMap<>();

        state.put("points", points);
        state.put("whiteBar", 0);
        state.put("blackBar", 0);
        state.put("whiteOff", 0);
        state.put("blackOff", 0);
        state.put("dice", new int[0]);

        return objectMapper.writeValueAsString(state);
    }
}