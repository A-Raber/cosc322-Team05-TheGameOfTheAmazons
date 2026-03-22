package ubc.cosc322.heuristic;

import ubc.cosc322.model.GameState;

/**
 * Phase-aware evaluator used by MCTSv2 rollouts.
 *
 * It combines relational territory with fast tactical terms to provide
 * stronger guidance than binary territory checks.
 */
public class PhaseAwareHeuristic {

    private static final int SIDE_TO_MOVE_BONUS = 10;
    private static final int ENDGAME_PHASE_ARROWS = 56;

    private final RelationalTerritorialHeuristic relational = new RelationalTerritorialHeuristic();
    private final ThreadLocal<int[]> myStatsLocal = ThreadLocal.withInitial(() -> new int[7]);
    private final ThreadLocal<int[]> oppStatsLocal = ThreadLocal.withInitial(() -> new int[7]);
    private final ThreadLocal<int[]> queenPosBufferLocal = ThreadLocal.withInitial(() -> new int[4]);

    public int evaluate(GameState state, int perspectiveSide) {
        int[] board = state.getBoardRef();
        int opponentSide = opposite(perspectiveSide);
        double phase = gamePhase(board);
        int[] myStats = myStatsLocal.get();
        int[] oppStats = oppStatsLocal.get();
        int[] queenPosBuffer = queenPosBufferLocal.get();

        computeQueenStats(board, perspectiveSide, myStats, queenPosBuffer);
        computeQueenStats(board, opponentSide, oppStats, queenPosBuffer);

        int territoryScore = relational.evaluate(state, perspectiveSide);

        int territoryWeight = lerp(2, 4, phase);
        int mobilityWeight = lerp(8, 6, phase);
        int trapWeight = lerp(95, 185, phase);
        int lowMobilityWeight = lerp(18, 42, phase);
        int centerWeight = lerp(4, 1, phase);
        int libertiesWeight = lerp(3, 12, phase);
        int minimumMobilityWeight = lerp(8, 24, phase);
        int spreadWeight = lerp(6, 1, phase);

        int score = 0;
        score += territoryWeight * territoryScore;
        score += mobilityWeight * (myStats[0] - oppStats[0]);
        score += trapWeight * (oppStats[1] - myStats[1]);
        score += lowMobilityWeight * (oppStats[2] - myStats[2]);
        score += centerWeight * (oppStats[3] - myStats[3]);
        score += libertiesWeight * (myStats[4] - oppStats[4]);
        score += minimumMobilityWeight * (myStats[5] - oppStats[5]);
        score += spreadWeight * (myStats[6] - oppStats[6]);

        if (state.getSideToMove() == perspectiveSide) {
            score += SIDE_TO_MOVE_BONUS;
        }

        return score;
    }

    // out[0] mobility, out[1] trapped queens, out[2] low-mobility penalty,
    // out[3] center-distance sum, out[4] adjacent liberties,
    // out[5] minimum queen mobility, out[6] queen spread
    private void computeQueenStats(int[] board, int side, int[] out, int[] queenPosBuffer) {
        int mobility = 0;
        int trapped = 0;
        int lowMobilityPenalty = 0;
        int centerDistance = 0;
        int adjacentLiberties = 0;
        int minMobility = Integer.MAX_VALUE;
        int queenCount = 0;

        for (int idx = 0; idx < GameState.BOARD_CELLS; idx++) {
            if (board[idx] != side) {
                continue;
            }

            int queenMobility = reachableCountFrom(board, idx);
            mobility += queenMobility;
            centerDistance += manhattanFromCenter(idx);
            adjacentLiberties += adjacentEmptyCount(board, idx);
            minMobility = Math.min(minMobility, queenMobility);
            if (queenCount < queenPosBuffer.length) {
                queenPosBuffer[queenCount] = idx;
            }
            queenCount++;

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
        out[4] = adjacentLiberties;
        out[5] = (queenCount == 0 || minMobility == Integer.MAX_VALUE) ? 0 : minMobility;
        out[6] = pairwiseQueenSpread(queenPosBuffer, Math.min(queenCount, queenPosBuffer.length));
    }

    private int adjacentEmptyCount(int[] board, int from) {
        int row = from / GameState.BOARD_SIZE;
        int col = from % GameState.BOARD_SIZE;
        int empties = 0;
        for (int dRow = -1; dRow <= 1; dRow++) {
            for (int dCol = -1; dCol <= 1; dCol++) {
                if (dRow == 0 && dCol == 0) {
                    continue;
                }
                int nr = row + dRow;
                int nc = col + dCol;
                if (nr < 0 || nr >= GameState.BOARD_SIZE || nc < 0 || nc >= GameState.BOARD_SIZE) {
                    continue;
                }
                int next = nr * GameState.BOARD_SIZE + nc;
                if (board[next] == GameState.EMPTY) {
                    empties++;
                }
            }
        }
        return empties;
    }

    private int pairwiseQueenSpread(int[] queenPositions, int queenCount) {
        int spread = 0;
        for (int i = 0; i < queenCount; i++) {
            int a = queenPositions[i];
            int aRow = a / GameState.BOARD_SIZE;
            int aCol = a % GameState.BOARD_SIZE;
            for (int j = i + 1; j < queenCount; j++) {
                int b = queenPositions[j];
                int bRow = b / GameState.BOARD_SIZE;
                int bCol = b % GameState.BOARD_SIZE;
                spread += Math.abs(aRow - bRow) + Math.abs(aCol - bCol);
            }
        }
        return spread;
    }

    private static double gamePhase(int[] board) {
        int arrows = 0;
        for (int cell : board) {
            if (cell == GameState.ARROW) {
                arrows++;
            }
        }
        return Math.min(1.0, arrows / (double) ENDGAME_PHASE_ARROWS);
    }

    private static int lerp(int early, int late, double phase) {
        return (int) Math.round(early + (late - early) * phase);
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
