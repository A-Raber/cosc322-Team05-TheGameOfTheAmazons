package ubc.cosc322;

import java.util.Arrays;

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

    private final int[] distMy = new int[GameState.BOARD_CELLS];
    private final int[] distOpp = new int[GameState.BOARD_CELLS];
    private final int[] queue = new int[GameState.BOARD_CELLS];

    public int evaluate(GameState state, int perspectiveSide) {
        int[] board = state.getBoardRef();
        int opponentSide = opposite(perspectiveSide);

        computeQueenDistanceMap(board, perspectiveSide, distMy);
        computeQueenDistanceMap(board, opponentSide, distOpp);

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

        int myMobility = pseudoMobility(board, perspectiveSide);
        int oppMobility = pseudoMobility(board, opponentSide);
        score += MOBILITY_WEIGHT * (myMobility - oppMobility);

        int myTrappedQueens = countTrappedQueens(board, perspectiveSide);
        int oppTrappedQueens = countTrappedQueens(board, opponentSide);
        score += TRAPPED_QUEEN_WEIGHT * (oppTrappedQueens - myTrappedQueens);

        int myLowMobilityPenalty = lowMobilityPenalty(board, perspectiveSide);
        int oppLowMobilityPenalty = lowMobilityPenalty(board, opponentSide);
        score += LOW_MOBILITY_WEIGHT * (oppLowMobilityPenalty - myLowMobilityPenalty);

        if (state.getSideToMove() == perspectiveSide) {
            score += SIDE_TO_MOVE_BONUS;
        }

        return score;
    }

    private void computeQueenDistanceMap(int[] board, int side, int[] dist) {
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

    private int pseudoMobility(int[] board, int side) {
        int mobility = 0;
        for (int idx = 0; idx < GameState.BOARD_CELLS; idx++) {
            if (board[idx] != side) {
                continue;
            }
            mobility += reachableCountFrom(board, idx);
        }
        return mobility;
    }

    private int countTrappedQueens(int[] board, int side) {
        int trapped = 0;
        for (int idx = 0; idx < GameState.BOARD_CELLS; idx++) {
            if (board[idx] != side) {
                continue;
            }
            if (!hasAnyReachableSquare(board, idx)) {
                trapped++;
            }
        }
        return trapped;
    }

    private int lowMobilityPenalty(int[] board, int side) {
        int penalty = 0;
        for (int idx = 0; idx < GameState.BOARD_CELLS; idx++) {
            if (board[idx] != side) {
                continue;
            }

            int mobility = reachableCountFrom(board, idx);
            if (mobility <= 2) {
                penalty += 20;
            } else if (mobility <= 5) {
                penalty += 8;
            }
        }
        return penalty;
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

    private boolean hasAnyReachableSquare(int[] board, int from) {
        for (int direction : DIRECTION_OFFSETS) {
            int next = from + direction;
            if (!isInsideBoard(next) || crossesRowBoundary(from, next)) {
                continue;
            }
            if (board[next] == GameState.EMPTY) {
                return true;
            }
        }
        return false;
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
