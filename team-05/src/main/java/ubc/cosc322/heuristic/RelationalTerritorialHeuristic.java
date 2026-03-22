package ubc.cosc322.heuristic;

import java.util.Arrays;

import ubc.cosc322.model.GameState;

public class RelationalTerritorialHeuristic {

    private static final int INF = 1_000_000;
    private static final int TERRITORY_BASE = 8;
    private static final int TERRITORY_RELATION_WEIGHT = 1;
    private static final int MOBILITY_WEIGHT = 5;
    private static final int TRAPPED_QUEEN_WEIGHT = 120;
    private static final int LOW_MOBILITY_WEIGHT = 18;
    private static final int SIDE_TO_MOVE_BONUS = 8;

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

    private final ThreadLocal<int[]> distMyLocal = ThreadLocal.withInitial(() -> new int[GameState.BOARD_CELLS]);
    private final ThreadLocal<int[]> distOppLocal = ThreadLocal.withInitial(() -> new int[GameState.BOARD_CELLS]);
    private final ThreadLocal<int[]> queueLocal = ThreadLocal.withInitial(() -> new int[GameState.BOARD_CELLS]);
    private final ThreadLocal<int[]> queenStatsLocal = ThreadLocal.withInitial(() -> new int[3]);

    public int evaluate(GameState state, int perspectiveSide) {
        int[] board = state.getBoardRef();
        int opponentSide = opposite(perspectiveSide);
        int[] distMy = distMyLocal.get();
        int[] distOpp = distOppLocal.get();
        int[] queue = queueLocal.get();
        int[] queenStats = queenStatsLocal.get();

        // --- Two BFS passes for queen-distance maps (unavoidable) ---
        computeQueenDistanceMap(board, perspectiveSide, distMy, queue);
        computeQueenDistanceMap(board, opponentSide, distOpp, queue);

        // --- Territory score from distance maps (single pass over board) ---
        int score = 0;
        for (int idx = 0; idx < GameState.BOARD_CELLS; idx++) {
            if (board[idx] != GameState.EMPTY) {
                continue;
            }

            int myD = distMy[idx];
            int oppD = distOpp[idx];
            if (myD < oppD) {
                score += TERRITORY_BASE + TERRITORY_RELATION_WEIGHT * (oppD - myD);
            } else if (oppD < myD) {
                score -= TERRITORY_BASE + TERRITORY_RELATION_WEIGHT * (myD - oppD);
            }
        }

        // --- Consolidated queen stats: one pass per side computes
        //     mobility, trapped count, and low-mobility penalty together ---
        computeQueenStats(board, perspectiveSide, queenStats);
        int myMobility       = queenStats[0];
        int myTrapped         = queenStats[1];
        int myLowMobPenalty   = queenStats[2];

        computeQueenStats(board, opponentSide, queenStats);
        int oppMobility       = queenStats[0];
        int oppTrapped        = queenStats[1];
        int oppLowMobPenalty  = queenStats[2];

        score += MOBILITY_WEIGHT      * (myMobility      - oppMobility);
        score += TRAPPED_QUEEN_WEIGHT * (oppTrapped       - myTrapped);
        score += LOW_MOBILITY_WEIGHT  * (oppLowMobPenalty - myLowMobPenalty);

        if (state.getSideToMove() == perspectiveSide) {
            score += SIDE_TO_MOVE_BONUS;
        }

        return score;
    }

    /**
     * Single pass over all queens of {@code side}: for each queen, compute
     * ray-based reachable count (mobility), and derive trapped / low-mobility
     * penalty from it.  Results written into {@code out[0..2]}.
     */
    private void computeQueenStats(int[] board, int side, int[] out) {
        int totalMobility = 0;
        int trapped = 0;
        int lowMobPenalty = 0;

        for (int idx = 0; idx < GameState.BOARD_CELLS; idx++) {
            if (board[idx] != side) {
                continue;
            }

            int mobility = reachableCountFrom(board, idx);
            totalMobility += mobility;

            if (mobility == 0) {
                trapped++;
            } else if (mobility <= 2) {
                lowMobPenalty += 20;
            } else if (mobility <= 5) {
                lowMobPenalty += 8;
            }
        }

        out[0] = totalMobility;
        out[1] = trapped;
        out[2] = lowMobPenalty;
    }

    private void computeQueenDistanceMap(int[] board, int side, int[] dist, int[] queue) {
        Arrays.fill(dist, INF);

        int head = 0;
        int tail = 0;

        for (int idx = 0; idx < GameState.BOARD_CELLS; idx++) {
            if (board[idx] == side) {
                dist[idx] = 0;
                queue[tail++] = idx;
            }
        }

        while (head < tail) {
            int current = queue[head++];
            int nextDistance = dist[current] + 1;

            for (int direction : DIRECTION_OFFSETS) {
                int ray = current;
                while (true) {
                    int next = ray + direction;
                    if (!isInsideBoard(next) || crossesRowBoundary(ray, next)) {
                        break;
                    }
                    if (board[next] != GameState.EMPTY) {
                        break;
                    }
                    if (dist[next] != INF) {
                        ray = next;
                        continue;
                    }
                    dist[next] = nextDistance;
                    queue[tail++] = next;
                    ray = next;
                }
            }
        }
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

    private static int opposite(int side) {
        return (side == GameState.BLACK) ? GameState.WHITE : GameState.BLACK;
    }

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
