package ubc.cosc322;

import java.util.ArrayList;

public class AlphaBetaMoveGenerator extends AbstractMoveGenerator {

    private static final int NEG_INF = -1_000_000_000;
    private static final int POS_INF = 1_000_000_000;
    private static final int MATE_SCORE = 5_000_000;
    private static final int MAX_PLY = 128;

    private final RelationalTerritorialHeuristic heuristic = new RelationalTerritorialHeuristic();

    private final MoveBuffer[] plyMoveBuffers = new MoveBuffer[MAX_PLY];
    private final int[] killerMoveA = new int[MAX_PLY];
    private final int[] killerMoveB = new int[MAX_PLY];
    private final int[][] history = new int[GameState.BOARD_CELLS][GameState.BOARD_CELLS];

    private Timer timer;

    public AlphaBetaMoveGenerator() {
        for (int i = 0; i < MAX_PLY; i++) {
            plyMoveBuffers[i] = new MoveBuffer(1024);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ArrayList<Integer>[] generateMove(GameState gameState) {
        GameState rootState = gameState.copy();
        int rootSide = rootState.getSideToMove();

        MoveBuffer rootMoves = plyMoveBuffers[0];
        rootMoves.clear();
        generateMoves(rootState, rootSide, rootMoves);

        if (rootMoves.size == 0) {
            return null;
        }

        timer = new Timer();
        timer.start();

        // Age history table: halve all entries to retain relative ordering
        // while decaying stale data from previous game positions
        for (int i = 0; i < GameState.BOARD_CELLS; i++) {
            for (int j = 0; j < GameState.BOARD_CELLS; j++) {
                history[i][j] >>= 1;
            }
        }
        // Clear killer moves from previous search
        java.util.Arrays.fill(killerMoveA, 0);
        java.util.Arrays.fill(killerMoveB, 0);

        int[] previousIterationScores = new int[rootMoves.size];
        for (int i = 0; i < previousIterationScores.length; i++) {
            previousIterationScores[i] = 0;
        }

        int bestFrom = rootMoves.from[0];
        int bestTo = rootMoves.to[0];
        int bestArrow = rootMoves.arrow[0];

        int depth = 1;
        while (!timer.timeUp()) {
            int iterationBestFrom = bestFrom;
            int iterationBestTo = bestTo;
            int iterationBestArrow = bestArrow;
            int iterationBestScore = NEG_INF;

            try {
                orderRootMoves(rootMoves, previousIterationScores, bestFrom, bestTo, bestArrow);

                int alpha = NEG_INF;
                int beta = POS_INF;

                for (int i = 0; i < rootMoves.size; i++) {
                    checkTimeout();

                    int from = rootMoves.from[i];
                    int to = rootMoves.to[i];
                    int arrow = rootMoves.arrow[i];

                    int movingPiece = doMove(rootState, from, to, arrow);
                    int score = -negamax(rootState, depth - 1, 1, -beta, -alpha);
                    rootState.undoMove(from, to, arrow, movingPiece);

                    previousIterationScores[i] = score;

                    if (score > iterationBestScore) {
                        iterationBestScore = score;
                        iterationBestFrom = from;
                        iterationBestTo = to;
                        iterationBestArrow = arrow;
                    }

                    if (score > alpha) {
                        alpha = score;
                    }
                }

                bestFrom = iterationBestFrom;
                bestTo = iterationBestTo;
                bestArrow = iterationBestArrow;
                depth++;
            } catch (SearchTimeout timeout) {
                break;
            }
        }

        return new ArrayList[] {
            toServerPosition(bestFrom),
            toServerPosition(bestTo),
            toServerPosition(bestArrow)
        };
    }

    private int negamax(GameState state, int depth, int ply, int alpha, int beta) {
        checkTimeout();

        int sideToMove = state.getSideToMove();

        if (depth <= 0) {
            if (!hasAnyLegalMove(state, sideToMove)) {
                return -MATE_SCORE + ply;
            }
            return heuristic.evaluate(state, sideToMove);
        }

        MoveBuffer moves = plyMoveBuffers[Math.min(ply, MAX_PLY - 1)];
        moves.clear();
        generateMoves(state, sideToMove, moves);

        if (moves.size == 0) {
            return -MATE_SCORE + ply;
        }

        orderMoves(ply, moves);

        int bestScore = NEG_INF;
        for (int i = 0; i < moves.size; i++) {
            selectBestScoredMove(moves, i);

            int from = moves.from[i];
            int to = moves.to[i];
            int arrow = moves.arrow[i];

            int movingPiece = doMove(state, from, to, arrow);
            int score = -negamax(state, depth - 1, ply + 1, -beta, -alpha);
            state.undoMove(from, to, arrow, movingPiece);

            if (score > bestScore) {
                bestScore = score;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                recordCutoffMove(ply, from, to, arrow, depth);
                break;
            }
        }

        return bestScore;
    }

    private void orderRootMoves(MoveBuffer rootMoves, int[] scores, int bestFrom, int bestTo, int bestArrow) {
        for (int i = 0; i < rootMoves.size; i++) {
            int score = scores[i];
            if (rootMoves.from[i] == bestFrom && rootMoves.to[i] == bestTo && rootMoves.arrow[i] == bestArrow) {
                score += 1_000_000;
            }
            rootMoves.score[i] = score;
        }

        for (int i = 0; i < rootMoves.size; i++) {
            selectBestScoredMove(rootMoves, i);
        }
    }

    private void orderMoves(int ply, MoveBuffer moves) {
        int killerA = killerMoveA[Math.min(ply, MAX_PLY - 1)];
        int killerB = killerMoveB[Math.min(ply, MAX_PLY - 1)];

        for (int i = 0; i < moves.size; i++) {
            int packed = packMove(moves.from[i], moves.to[i], moves.arrow[i]);
            int score = history[moves.from[i]][moves.to[i]];

            if (packed == killerA) {
                score += 900_000;
            } else if (packed == killerB) {
                score += 700_000;
            }

            moves.score[i] = score;
        }
    }

    private void selectBestScoredMove(MoveBuffer moves, int fromIndex) {
        int bestIndex = fromIndex;
        int bestScore = moves.score[fromIndex];

        for (int i = fromIndex + 1; i < moves.size; i++) {
            if (moves.score[i] > bestScore) {
                bestScore = moves.score[i];
                bestIndex = i;
            }
        }

        if (bestIndex != fromIndex) {
            moves.swap(fromIndex, bestIndex);
        }
    }

    private int doMove(GameState state, int from, int to, int arrow) {
        int[] board = state.getBoardRef();
        int movingPiece = board[from];
        board[from] = GameState.EMPTY;
        board[to] = movingPiece;
        board[arrow] = GameState.ARROW;
        state.setSideToMove(opposite(state.getSideToMove()));
        return movingPiece;
    }

    private void generateMoves(GameState state, int side, MoveBuffer out) {
        int[] board = state.getBoardRef();

        for (int from = 0; from < GameState.BOARD_CELLS; from++) {
            if (board[from] != side) {
                continue;
            }

            for (int moveDirection : DIRECTION_OFFSETS) {
                int moveRay = from;
                while (true) {
                    int to = moveRay + moveDirection;
                    if (!isInsideBoard(to) || crossesRowBoundary(moveRay, to)) {
                        break;
                    }
                    if (board[to] != GameState.EMPTY) {
                        break;
                    }

                    for (int arrowDirection : DIRECTION_OFFSETS) {
                        int arrowRay = to;
                        while (true) {
                            int arrow = arrowRay + arrowDirection;
                            if (!isInsideBoard(arrow) || crossesRowBoundary(arrowRay, arrow)) {
                                break;
                            }

                            if (!isArrowCellEmpty(board, arrow, from, to)) {
                                break;
                            }

                            out.add(from, to, arrow, 0);
                            arrowRay = arrow;
                        }
                    }

                    moveRay = to;
                }
            }
        }
    }

    private boolean isArrowCellEmpty(int[] board, int idx, int from, int to) {
        if (idx == from) {
            return true;
        }
        if (idx == to) {
            return false;
        }
        return board[idx] == GameState.EMPTY;
    }

    private void recordCutoffMove(int ply, int from, int to, int arrow, int depth) {
        int boundedPly = Math.min(ply, MAX_PLY - 1);
        int packed = packMove(from, to, arrow);

        if (killerMoveA[boundedPly] != packed) {
            killerMoveB[boundedPly] = killerMoveA[boundedPly];
            killerMoveA[boundedPly] = packed;
        }

        history[from][to] += depth * depth;
    }

    private void checkTimeout() {
        if (timer != null && timer.timeUp()) {
            throw SearchTimeout.INSTANCE;
        }
    }

    private static int packMove(int from, int to, int arrow) {
        return from | (to << 7) | (arrow << 14);
    }

    private static int opposite(int side) {
        return (side == GameState.BLACK) ? GameState.WHITE : GameState.BLACK;
    }

    private static final class SearchTimeout extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private static final SearchTimeout INSTANCE = new SearchTimeout();

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    private static final class MoveBuffer {
        int[] from;
        int[] to;
        int[] arrow;
        int[] score;
        int size;

        MoveBuffer(int initialCapacity) {
            from = new int[initialCapacity];
            to = new int[initialCapacity];
            arrow = new int[initialCapacity];
            score = new int[initialCapacity];
            size = 0;
        }

        void clear() {
            size = 0;
        }

        void add(int fromValue, int toValue, int arrowValue, int scoreValue) {
            ensureCapacity(size + 1);
            from[size] = fromValue;
            to[size] = toValue;
            arrow[size] = arrowValue;
            score[size] = scoreValue;
            size++;
        }

        void ensureCapacity(int wanted) {
            if (wanted <= from.length) {
                return;
            }
            int newSize = Math.max(wanted, from.length * 2);
            from = grow(from, newSize);
            to = grow(to, newSize);
            arrow = grow(arrow, newSize);
            score = grow(score, newSize);
        }

        void swap(int a, int b) {
            int tmp;

            tmp = from[a];
            from[a] = from[b];
            from[b] = tmp;

            tmp = to[a];
            to[a] = to[b];
            to[b] = tmp;

            tmp = arrow[a];
            arrow[a] = arrow[b];
            arrow[b] = tmp;

            tmp = score[a];
            score[a] = score[b];
            score[b] = tmp;
        }

        private int[] grow(int[] source, int newLength) {
            int[] grown = new int[newLength];
            System.arraycopy(source, 0, grown, 0, source.length);
            return grown;
        }
    }
}
