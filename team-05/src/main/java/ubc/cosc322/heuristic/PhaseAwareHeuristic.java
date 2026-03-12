package ubc.cosc322.heuristic;

import ubc.cosc322.model.GameState;

/**
 * Phase-aware evaluator used by MCTSv2 rollouts.
 *
 * It combines relational territory with fast tactical terms to provide
 * stronger guidance than binary territory checks.
 */
public class PhaseAwareHeuristic {

    private static final int TERRITORY_WEIGHT = 3;
    private static final int MOBILITY_WEIGHT = 7;
    private static final int TRAP_WEIGHT = 140;
    private static final int LOW_MOBILITY_WEIGHT = 28;
    private static final int CENTER_WEIGHT = 2;
    private static final int SIDE_TO_MOVE_BONUS = 10;

    private final RelationalTerritorialHeuristic relational = new RelationalTerritorialHeuristic();
    private final int[] myStats = new int[4];
    private final int[] oppStats = new int[4];

    public int evaluate(GameState state, int perspectiveSide) {
        int[] board = state.getBoardRef();
        int opponentSide = opposite(perspectiveSide);

        computeQueenStats(board, perspectiveSide, myStats);
        computeQueenStats(board, opponentSide, oppStats);

        int territoryScore = relational.evaluate(state, perspectiveSide);

        int score = 0;
        score += TERRITORY_WEIGHT * territoryScore;
        score += MOBILITY_WEIGHT * (myStats[0] - oppStats[0]);
        score += TRAP_WEIGHT * (oppStats[1] - myStats[1]);
        score += LOW_MOBILITY_WEIGHT * (oppStats[2] - myStats[2]);
        score += CENTER_WEIGHT * (oppStats[3] - myStats[3]);

        if (state.getSideToMove() == perspectiveSide) {
            score += SIDE_TO_MOVE_BONUS;
        }

        return score;
    }

    // out[0] mobility, out[1] trapped queens, out[2] low-mobility penalty, out[3] center-distance sum
    private void computeQueenStats(int[] board, int side, int[] out) {
        int mobility = 0;
        int trapped = 0;
        int lowMobilityPenalty = 0;
        int centerDistance = 0;

        for (int idx = 0; idx < GameState.BOARD_CELLS; idx++) {
            if (board[idx] != side) {
                continue;
            }

            int queenMobility = reachableCountFrom(board, idx);
            mobility += queenMobility;
            centerDistance += manhattanFromCenter(idx);

            if (queenMobility == 0) {
                trapped++;
            } else if (queenMobility <= 2) {
                lowMobilityPenalty += 24;
            } else if (queenMobility <= 5) {
                lowMobilityPenalty += 10;
            }
        }

        out[0] = mobility;
        out[1] = trapped;
        out[2] = lowMobilityPenalty;
        out[3] = centerDistance;
    }

    private int reachableCountFrom(int[] board, int from) {
        int total = 0;
        for (int direction : DIRECTION_OFFSETS) {
            int ray = from;
            while (true) {
                int next = ray + direction;
                if (!isInsideBoard(next) || crossesRowBoundary(ray, next)) {
                    break;
                }
                if (board[next] != GameState.EMPTY) {
                    break;
                }
                total++;
                ray = next;
            }
        }
        return total;
    }

    private int manhattanFromCenter(int index) {
        int row = index / GameState.BOARD_SIZE;
        int col = index % GameState.BOARD_SIZE;
        return Math.abs((GameState.BOARD_SIZE - 1) - 2 * row) + Math.abs((GameState.BOARD_SIZE - 1) - 2 * col);
    }

    private static int opposite(int side) {
        return (side == GameState.BLACK) ? GameState.WHITE : GameState.BLACK;
    }

    private static final int[] DIRECTION_OFFSETS = {
        -GameState.BOARD_SIZE,
        GameState.BOARD_SIZE,
        -1,
        1,
        -GameState.BOARD_SIZE - 1,
        -GameState.BOARD_SIZE + 1,
        GameState.BOARD_SIZE - 1,
        GameState.BOARD_SIZE + 1
    };

    private static boolean isInsideBoard(int index) {
        return index >= 0 && index < GameState.BOARD_CELLS;
    }

    private static boolean crossesRowBoundary(int from, int to) {
        return Math.abs(columnOf(from) - columnOf(to)) > 1;
    }

    private static int columnOf(int index) {
        return index % GameState.BOARD_SIZE;
    }
}
