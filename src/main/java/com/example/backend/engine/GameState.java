package com.example.backend.engine;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The full persisted game state: the {@link BoardState} layout plus the
 * dice values still available to play this turn. This is the shape
 * stored in {@code Game#currentState} (see {@code InitialGameStateFactory}).
 * An empty {@code dice} list means the current player must roll before
 * moving (or just finished their turn).
 */
public class GameState {

    private BoardState board;
    private List<Integer> dice;

    public GameState(BoardState board, List<Integer> dice) {
        this.board = board;
        this.dice = dice;
    }

    public static GameState fromJson(ObjectMapper objectMapper, String json) {
        JsonNode node = objectMapper.readTree(json);
        BoardState board = objectMapper.treeToValue(node, BoardState.class);

        List<Integer> dice = new ArrayList<>();
        JsonNode diceNode = node.get("dice");
        if (diceNode != null) {
            diceNode.forEach(d -> dice.add(d.asInt()));
        }

        return new GameState(board, dice);
    }

    public String toJson(ObjectMapper objectMapper) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("points", board.getPoints());
        map.put("whiteBar", board.getWhiteBar());
        map.put("blackBar", board.getBlackBar());
        map.put("whiteOff", board.getWhiteOff());
        map.put("blackOff", board.getBlackOff());
        map.put("dice", dice);
        return objectMapper.writeValueAsString(map);
    }

    public BoardState getBoard() {
        return board;
    }

    public void setBoard(BoardState board) {
        this.board = board;
    }

    public List<Integer> getDice() {
        return dice;
    }

    public void setDice(List<Integer> dice) {
        this.dice = dice;
    }
}
