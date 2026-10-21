package com.example.backend.service;

import com.example.backend.dto.response.GameResponse;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Pushes game-state updates to {@code /topic/games/{gameId}} so connected
 * players are notified in real time (new dice roll, a move applied, turn
 * changed, game finished) instead of having to poll the REST API.
 */
@Service
public class GameUpdateBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public GameUpdateBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(GameResponse game) {
        messagingTemplate.convertAndSend("/topic/games/" + game.id(), game);
    }
}
