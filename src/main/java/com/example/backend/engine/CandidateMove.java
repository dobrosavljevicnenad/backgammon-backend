package com.example.backend.engine;

/**
 * A single legal checker move for one die value, computed against a
 * particular {@link BoardState} snapshot.
 *
 * @param from     {@link BoardState#BAR} (0) or a board point (1..24)
 * @param to       a board point (1..24) or {@link BoardState#OFF} (25)
 * @param dieValue the die value (1..6) this move consumes
 */
public record CandidateMove(int from, int to, int dieValue) {
}
