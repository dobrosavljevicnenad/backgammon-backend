package com.example.backend.engine;

import com.example.backend.enums.CheckerColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineTest {

    /** Standard backgammon starting position, matching InitialGameStateFactory. */
    private static BoardState initialBoard() {
        int[] points = new int[25];
        points[1] = -2;
        points[6] = 5;
        points[8] = 3;
        points[12] = -5;
        points[13] = 5;
        points[17] = -3;
        points[19] = -5;
        points[24] = 2;
        return new BoardState(points, 0, 0, 0, 0);
    }

    @Test
    void whiteOpeningMoveFromTwentyFourIsLegalOnOpenPoint() {
        BoardState board = initialBoard();

        // White 24 -> 23 with die 1: point 23 is empty, so it's open.
        List<CandidateMove> moves = GameEngine.legalMovesForDie(board, CheckerColor.WHITE, 1);

        assertTrue(moves.contains(new CandidateMove(24, 23, 1)));
    }

    @Test
    void whiteCannotLandOnPointWithTwoOrMoreBlackCheckers() {
        BoardState board = initialBoard();

        // White 24 -> 19 with die 5: point 19 has 5 BLACK checkers, blocked.
        List<CandidateMove> moves = GameEngine.legalMovesForDie(board, CheckerColor.WHITE, 5);

        assertFalse(moves.contains(new CandidateMove(24, 19, 5)));
    }

    @Test
    void blackCannotLandOnPointWithTwoOrMoreWhiteCheckers() {
        BoardState board = initialBoard();

        // Black 1 -> 6 with die 5: point 6 has 5 WHITE checkers, blocked.
        List<CandidateMove> moves = GameEngine.legalMovesForDie(board, CheckerColor.BLACK, 5);

        assertFalse(moves.contains(new CandidateMove(1, 6, 5)));
    }

    @Test
    void checkersOnBarMustEnterBeforeAnyOtherMove() {
        BoardState board = initialBoard();
        board.setBarCount(CheckerColor.WHITE, 1);

        List<CandidateMove> moves = GameEngine.legalMovesForDie(board, CheckerColor.WHITE, 3);

        // Die 3 enters WHITE at point 22 (25 - 3), which is open.
        assertEquals(List.of(new CandidateMove(BoardState.BAR, 22, 3)), moves);
    }

    @Test
    void entryFromBarBlockedWhenEntryPointIsCoveredByOpponent() {
        BoardState board = initialBoard();
        board.setBarCount(CheckerColor.WHITE, 1);

        // Die 6 would enter WHITE at point 19, which has 5 BLACK checkers.
        List<CandidateMove> moves = GameEngine.legalMovesForDie(board, CheckerColor.WHITE, 6);

        assertTrue(moves.isEmpty());
    }

    @Test
    void movingOntoLoneOpponentCheckerIsLegalHit() {
        BoardState board = initialBoard();
        // Place a lone BLACK blot on point 20.
        board.set(20, -1);

        List<CandidateMove> moves = GameEngine.legalMovesForDie(board, CheckerColor.WHITE, 4);

        assertTrue(moves.contains(new CandidateMove(24, 20, 4)));
    }

    @Test
    void applyingHitSendsSingleOpponentCheckerToBar() {
        BoardState board = initialBoard();
        board.set(20, -1);

        BoardState next = GameEngine.apply(board, CheckerColor.WHITE, new CandidateMove(24, 20, 4));

        assertEquals(1, next.at(20));
        assertEquals(1, next.at(24));
        assertEquals(1, next.barCount(CheckerColor.BLACK));
    }

    @Test
    void bearOffNotAllowedUntilAllCheckersAreHome() {
        BoardState board = initialBoard();

        assertFalse(GameEngine.allCheckersHome(board, CheckerColor.WHITE));

        List<CandidateMove> moves = GameEngine.legalMovesForDie(board, CheckerColor.WHITE, 6);
        assertTrue(moves.stream().noneMatch(m -> m.to() == BoardState.OFF));
    }

    @Test
    void exactBearOffAllowedWhenAllCheckersHome() {
        int[] points = new int[25];
        points[6] = 2;
        points[3] = 13;
        BoardState board = new BoardState(points, 0, 0, 0, 0);

        assertTrue(GameEngine.allCheckersHome(board, CheckerColor.WHITE));

        List<CandidateMove> moves = GameEngine.legalMovesForDie(board, CheckerColor.WHITE, 6);

        assertTrue(moves.contains(new CandidateMove(6, BoardState.OFF, 6)));
    }

    @Test
    void overshootBearOffOnlyAllowedWhenNoCheckerFartherFromEdge() {
        int[] points = new int[25];
        points[3] = 15; // all checkers on point 3, nothing higher in the home board
        BoardState board = new BoardState(points, 0, 0, 0, 0);

        // Die 6 overshoots point 3's exact bear-off distance (3), but is allowed
        // since no WHITE checker sits on points 4-6.
        List<CandidateMove> moves = GameEngine.legalMovesForDie(board, CheckerColor.WHITE, 6);

        assertTrue(moves.contains(new CandidateMove(3, BoardState.OFF, 6)));
    }

    @Test
    void overshootBearOffBlockedWhenCheckerFartherFromEdgeExists() {
        int[] points = new int[25];
        points[3] = 14;
        points[6] = 1; // one checker farther from the edge
        BoardState board = new BoardState(points, 0, 0, 0, 0);

        List<CandidateMove> moves = GameEngine.legalMovesForDie(board, CheckerColor.WHITE, 6);

        assertFalse(moves.contains(new CandidateMove(3, BoardState.OFF, 6)));
    }

    @Test
    void hasAnyLegalMoveFalseWhenFullyBlocked() {
        int[] points = new int[25];
        points[24] = 1;
        // Surround with BLACK blocks (2+) on every point WHITE could reach with 1-6.
        for (int p = 18; p <= 23; p++) {
            points[p] = -2;
        }
        BoardState board = new BoardState(points, 0, 0, 0, 0);

        assertFalse(GameEngine.hasAnyLegalMove(board, CheckerColor.WHITE, List.of(1, 2, 3, 4, 5, 6)));
    }
}
