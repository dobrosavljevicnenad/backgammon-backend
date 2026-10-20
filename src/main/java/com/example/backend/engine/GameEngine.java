package com.example.backend.engine;

import com.example.backend.enums.CheckerColor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure backgammon rules engine: given a {@link BoardState} snapshot, a
 * player's {@link CheckerColor} and a die value, computes which moves
 * are legal.
 * <p>
 * Board convention (matches {@code InitialGameStateFactory} / {@code Move}):
 * <ul>
 *     <li>Points are numbered 1..24.</li>
 *     <li>WHITE moves in decreasing point order (24 -> 1) and bears off past point 1.</li>
 *     <li>BLACK moves in increasing point order (1 -> 24) and bears off past point 24.</li>
 *     <li>{@link BoardState#BAR} (0) represents a checker on the bar.</li>
 *     <li>{@link BoardState#OFF} (25) represents a borne-off checker.</li>
 * </ul>
 * This class only reasons about a single die value against a single
 * board snapshot - it does not sequence an entire turn (using both dice,
 * updating the board between moves). That sequencing belongs in a
 * service layer that repeatedly applies {@link #apply} and re-queries
 * this engine.
 */
public final class GameEngine {

    private GameEngine() {
    }

    public static int direction(CheckerColor color) {
        return color == CheckerColor.WHITE ? -1 : 1;
    }

    public static boolean isOnBar(BoardState state, CheckerColor color) {
        return state.barCount(color) > 0;
    }

    /**
     * The point a checker enters on when coming off the bar for the given die.
     * WHITE enters in the opponent's home board (19..24); BLACK enters in
     * points 1..6.
     */
    public static int barEntryPoint(CheckerColor color, int dieValue) {
        return color == CheckerColor.WHITE ? (25 - dieValue) : dieValue;
    }

    /**
     * True once every checker of {@code color} is in its home board and
     * none are on the bar - the prerequisite for bearing off.
     * WHITE home board: points 1..6. BLACK home board: points 19..24.
     */
    public static boolean allCheckersHome(BoardState state, CheckerColor color) {
        if (isOnBar(state, color)) {
            return false;
        }
        int[] points = state.getPoints();
        if (color == CheckerColor.WHITE) {
            for (int p = 7; p <= 24; p++) {
                if (points[p] > 0) {
                    return false;
                }
            }
        } else {
            for (int p = 1; p <= 18; p++) {
                if (points[p] < 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * True if {@code color} may legally land a checker on {@code point}:
     * the point is empty, occupied by {@code color} itself, or occupied
     * by exactly one opposing checker (a "blot", which gets hit).
     */
    public static boolean canLandOn(BoardState state, CheckerColor color, int point) {
        int value = state.at(point);
        return color == CheckerColor.WHITE ? value >= -1 : value <= 1;
    }

    /**
     * All legal moves for {@code color} using a single {@code dieValue}
     * against the given board snapshot. If the player has checkers on
     * the bar, only bar-entry moves are returned (bar entry is mandatory
     * before any other checker may move).
     */
    public static List<CandidateMove> legalMovesForDie(BoardState state, CheckerColor color, int dieValue) {
        if (dieValue < 1 || dieValue > 6) {
            throw new IllegalArgumentException("dieValue must be 1..6, was " + dieValue);
        }

        List<CandidateMove> moves = new ArrayList<>();

        if (isOnBar(state, color)) {
            int entryPoint = barEntryPoint(color, dieValue);
            if (canLandOn(state, color, entryPoint)) {
                moves.add(new CandidateMove(BoardState.BAR, entryPoint, dieValue));
            }
            return moves;
        }

        boolean canBearOff = allCheckersHome(state, color);
        int[] points = state.getPoints();

        if (color == CheckerColor.WHITE) {
            for (int from = 1; from <= 24; from++) {
                if (points[from] <= 0) {
                    continue;
                }
                int to = from - dieValue;
                if (to >= 1) {
                    if (canLandOn(state, color, to)) {
                        moves.add(new CandidateMove(from, to, dieValue));
                    }
                } else if (canBearOff && isBearOffMove(points, from, to, 1, 6, true)) {
                    moves.add(new CandidateMove(from, BoardState.OFF, dieValue));
                }
            }
        } else {
            for (int from = 1; from <= 24; from++) {
                if (points[from] >= 0) {
                    continue;
                }
                int to = from + dieValue;
                if (to <= 24) {
                    if (canLandOn(state, color, to)) {
                        moves.add(new CandidateMove(from, to, dieValue));
                    }
                } else if (canBearOff && isBearOffMove(points, from, to, 19, 24, false)) {
                    moves.add(new CandidateMove(from, BoardState.OFF, dieValue));
                }
            }
        }

        return moves;
    }

    /**
     * A bear-off beyond the board edge is only legal with an exact die,
     * UNLESS no checkers of this color occupy a higher point in the home
     * board (further from the edge) - in which case an overshoot is allowed.
     *
     * @param homeNearEdge  home-board bound closest to the exit edge
     * @param homeFarEdge   home-board bound furthest from the exit edge
     * @param whiteLike     true for WHITE (checks points with positive values),
     *                      false for BLACK (checks points with negative values)
     */
    private static boolean isBearOffMove(int[] points, int from, int to, int homeNearEdge, int homeFarEdge, boolean whiteLike) {
        boolean exact = whiteLike ? to == 0 : to == 25;
        if (exact) {
            return true;
        }
        // Overshoot: only legal if no checker sits further from the edge than `from`.
        if (whiteLike) {
            for (int p = from + 1; p <= homeFarEdge; p++) {
                if (points[p] > 0) {
                    return false;
                }
            }
        } else {
            for (int p = homeNearEdge; p <= from - 1; p++) {
                if (points[p] < 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * All legal moves for {@code color} across a full dice roll (e.g.
     * {@code [3, 5]} or a double like {@code [4, 4, 4, 4]}), computed
     * independently against the single given board snapshot (one die
     * value at a time, not sequenced).
     */
    public static List<CandidateMove> legalMoves(BoardState state, CheckerColor color, List<Integer> dice) {
        List<CandidateMove> moves = new ArrayList<>();
        Set<Integer> seenDieValues = new LinkedHashSet<>();
        for (int die : dice) {
            if (seenDieValues.add(die)) {
                moves.addAll(legalMovesForDie(state, color, die));
            }
        }
        return moves;
    }

    /**
     * Whether {@code color} has at least one legal move for any value in
     * {@code dice}. If false, the player's turn must be forfeited.
     */
    public static boolean hasAnyLegalMove(BoardState state, CheckerColor color, List<Integer> dice) {
        for (int die : dice) {
            if (!legalMovesForDie(state, color, die).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the exact (from, to) move is legal for {@code color} with
     * {@code dieValue} against the given board snapshot.
     */
    public static boolean isLegalMove(BoardState state, CheckerColor color, int from, int to, int dieValue) {
        for (CandidateMove move : legalMovesForDie(state, color, dieValue)) {
            if (move.from() == from && move.to() == to) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies {@code move} to a copy of {@code state} for {@code color},
     * handling bar entry, bear-off and hitting a lone opposing checker
     * ("blot"), and returns the resulting board. Does not validate
     * legality - callers should check {@link #isLegalMove} first.
     */
    public static BoardState apply(BoardState state, CheckerColor color, CandidateMove move) {
        BoardState next = state.copy();
        CheckerColor opponent = color == CheckerColor.WHITE ? CheckerColor.BLACK : CheckerColor.WHITE;

        if (move.from() == BoardState.BAR) {
            next.setBarCount(color, next.barCount(color) - 1);
        } else {
            // Remove one checker of `color` from the origin point.
            int originValue = next.at(move.from());
            next.set(move.from(), color == CheckerColor.WHITE ? originValue - 1 : originValue + 1);
        }

        if (move.to() == BoardState.OFF) {
            next.setOffCount(color, next.offCount(color) + 1);
        } else {
            int destinationValue = next.at(move.to());
            if (color == CheckerColor.WHITE) {
                if (destinationValue == -1) {
                    next.setBarCount(opponent, next.barCount(opponent) + 1);
                    destinationValue = 0;
                }
                next.set(move.to(), destinationValue + 1);
            } else {
                if (destinationValue == 1) {
                    next.setBarCount(opponent, next.barCount(opponent) + 1);
                    destinationValue = 0;
                }
                next.set(move.to(), destinationValue - 1);
            }
        }

        return next;
    }
}
