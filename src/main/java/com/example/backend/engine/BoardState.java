package com.example.backend.engine;

import com.example.backend.enums.CheckerColor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;

/**
 * Pure, persistence-agnostic representation of a backgammon board.
 * <p>
 * Index 0 of {@code points} is unused. Indexes 1..24 are the 24 board
 * points. A positive value means that many WHITE checkers occupy the
 * point; a negative value means that many BLACK checkers occupy it.
 * <p>
 * Mirrors the JSON shape produced by {@code InitialGameStateFactory}
 * (plus the "dice" field, which this class ignores - dice are handled
 * separately from board layout).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BoardState {

    public static final int POINT_COUNT = 24;
    public static final int BAR = 0;
    public static final int OFF = 25;

    private int[] points;
    private int whiteBar;
    private int blackBar;
    private int whiteOff;
    private int blackOff;

    public BoardState() {
        this.points = new int[POINT_COUNT + 1];
    }

    public BoardState(int[] points, int whiteBar, int blackBar, int whiteOff, int blackOff) {
        if (points.length != POINT_COUNT + 1) {
            throw new IllegalArgumentException("points must have length " + (POINT_COUNT + 1));
        }
        this.points = points;
        this.whiteBar = whiteBar;
        this.blackBar = blackBar;
        this.whiteOff = whiteOff;
        this.blackOff = blackOff;
    }

    public static BoardState fromJson(ObjectMapper objectMapper, String json) {
        return objectMapper.readValue(json, BoardState.class);
    }

    public String toJson(ObjectMapper objectMapper) {
        return objectMapper.writeValueAsString(this);
    }

    public BoardState copy() {
        return new BoardState(points.clone(), whiteBar, blackBar, whiteOff, blackOff);
    }

    public int at(int point) {
        return points[point];
    }

    public void set(int point, int value) {
        points[point] = value;
    }

    public int barCount(CheckerColor color) {
        return color == CheckerColor.WHITE ? whiteBar : blackBar;
    }

    public void setBarCount(CheckerColor color, int value) {
        if (color == CheckerColor.WHITE) {
            whiteBar = value;
        } else {
            blackBar = value;
        }
    }

    public int offCount(CheckerColor color) {
        return color == CheckerColor.WHITE ? whiteOff : blackOff;
    }

    public void setOffCount(CheckerColor color, int value) {
        if (color == CheckerColor.WHITE) {
            whiteOff = value;
        } else {
            blackOff = value;
        }
    }

    @JsonProperty("points")
    public int[] getPoints() {
        return points;
    }

    public void setPoints(int[] points) {
        this.points = points;
    }

    @JsonProperty("whiteBar")
    public int getWhiteBar() {
        return whiteBar;
    }

    public void setWhiteBar(int whiteBar) {
        this.whiteBar = whiteBar;
    }

    @JsonProperty("blackBar")
    public int getBlackBar() {
        return blackBar;
    }

    public void setBlackBar(int blackBar) {
        this.blackBar = blackBar;
    }

    @JsonProperty("whiteOff")
    public int getWhiteOff() {
        return whiteOff;
    }

    public void setWhiteOff(int whiteOff) {
        this.whiteOff = whiteOff;
    }

    @JsonProperty("blackOff")
    public int getBlackOff() {
        return blackOff;
    }

    public void setBlackOff(int blackOff) {
        this.blackOff = blackOff;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BoardState other)) return false;
        return whiteBar == other.whiteBar
                && blackBar == other.blackBar
                && whiteOff == other.whiteOff
                && blackOff == other.blackOff
                && Arrays.equals(points, other.points);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(points);
        result = 31 * result + whiteBar;
        result = 31 * result + blackBar;
        result = 31 * result + whiteOff;
        result = 31 * result + blackOff;
        return result;
    }

    @Override
    public String toString() {
        return "BoardState{points=" + Arrays.toString(points)
                + ", whiteBar=" + whiteBar
                + ", blackBar=" + blackBar
                + ", whiteOff=" + whiteOff
                + ", blackOff=" + blackOff
                + '}';
    }
}
