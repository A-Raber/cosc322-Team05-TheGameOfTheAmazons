package ubc.cosc322.engine.mcts.v2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

import ubc.cosc322.engine.AbstractMoveGenerator;
import ubc.cosc322.heuristic.PhaseAwareHeuristic;
import ubc.cosc322.model.GameState;
import ubc.cosc322.model.Move;
import ubc.cosc322.util.Timer;

/**
 * MCTSv2 is memory-optimized to v1:
 * - Node does not store a full GameState copy.
 * - Expansion is lazy (one child per visit), not full fan-out per visit.
 * - Playout move selection uses reservoir sampling (no full move list allocation).
 */
public class MCTSv2 extends AbstractMoveGenerator {

    private static final double EXPLORATION_CONSTANT = Math.sqrt(2.0);
    private static final double PRIOR_WEIGHT = 0.18;
    private static final double PROGRESSIVE_WIDENING_C = 1.9;
    private static final double PROGRESSIVE_WIDENING_ALPHA = 0.55;

    private static final int DEFAULT_MAX_NODES = 1_200_000;
    private static final int MAX_PLAYOUT_DEPTH = 30;
    private static final int MAX_ITERATIONS = 120_000;
    private static final int MAX_CANDIDATE_MOVES = 160;
    private static final int MAX_ARROW_CANDIDATES_PER_DEST = 3;
    private static final int EXPANSION_TOP_BAND = 14;
    private static final int OPENING_PLY_THRESHOLD = 16;

    private static final double PLAYOUT_RANDOM_MOVE_RATE = 0.30;
    private static final int PLAYOUT_TACTICAL_SAMPLE_SIZE = 20;

    private final Random random = new Random();
    private final PhaseAwareHeuristic heuristic = new PhaseAwareHeuristic();

    private final int maxNodes;
    private int nodeCount;
    private boolean warnedNodeCap;
    private int lastMovedQueenBlack = -1;
    private int lastMovedQueenWhite = -1;

    public MCTSv2() {
        this(DEFAULT_MAX_NODES);
    }

    public MCTSv2(int maxNodes) {
        this.maxNodes = Math.max(1, maxNodes);
    }

    private static final class ScoredMove {
        final Move move;
        final double score;
        final double prior;

        ScoredMove(Move move, double score) {
            this.move = move;
            this.score = score;
            this.prior = sigmoid(score / 28.0);
        }
    }

    private static final class Node {
        final Move moveFromParent;
        final double prior;
        final ArrayList<Node> children = new ArrayList<>();

        List<ScoredMove> unexpandedMoves;
        int visitCount;
        double valueSum;

        Node(Move moveFromParent, double prior) {
            this.moveFromParent = moveFromParent;
            this.prior = prior;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ArrayList<Integer>[] generateMove(GameState gameState) {
        Timer timer = new Timer();
        timer.start();

        Node root = new Node(null, 0.5);
        int rootPlayer = gameState.getSideToMove();
        int rootPly = countArrows(gameState.getBoardRef());
        boolean openingPhase = rootPly < OPENING_PLY_THRESHOLD;

        nodeCount = 1;
        warnedNodeCap = false;
        int iterations = 0;

        while (!timer.timeUp() && iterations < MAX_ITERATIONS && nodeCount < maxNodes) {
            GameState rolloutState = gameState.copy();
            ArrayList<Node> path = new ArrayList<>(32);
            Node node = root;
            path.add(node);

            // 1) Selection with progressive widening.
            while (true) {
                ensureUnexpandedMoves(node, rolloutState);
                if (canExpandHere(node)) {
                    break;
                }
                if (node.children.isEmpty()) {
                    break;
                }
                node = bestChildByUct(node);
                applyMove(rolloutState, node.moveFromParent);
                path.add(node);
            }

            // 2) Expansion: add a single child lazily.
            if (canExpandHere(node)) {
                if (nodeCount >= maxNodes) {
                    warnNodeCap();
                } else {
                    ScoredMove selected = removeBiasedTopMove(node.unexpandedMoves);
                    Node child = new Node(selected.move, selected.prior);
                    node.children.add(child);
                    nodeCount++;

                    applyMove(rolloutState, selected.move);
                    node = child;
                    path.add(node);
                }
            }

            // 3) Simulation + 4) Backpropagation.
            int[] simLastMovedQueen = new int[] { lastMovedQueenBlack, lastMovedQueenWhite };
            double result = simulatePlayout(rolloutState, rootPlayer, simLastMovedQueen);
            for (Node visited : path) {
                visited.visitCount++;
                visited.valueSum += result;
            }

            iterations++;
        }

        Node bestChild = selectBestRootChild(root, gameState, openingPhase, rootPly);
        if (bestChild == null || bestChild.moveFromParent == null) {
            return null;
        }

        Move bestMove = bestChild.moveFromParent;
        rememberMovedQueen(rootPlayer, bestMove.to);
        return new ArrayList[] {
                toServerPosition(bestMove.from),
                toServerPosition(bestMove.to),
                toServerPosition(bestMove.arrow)
        };
    }

    private Node selectBestRootChild(Node root, GameState state, boolean openingPhase, int ply) {
        if (root.children.isEmpty()) {
            return null;
        }

        if (!openingPhase) {
            return root.children.stream().max(Comparator.comparingDouble(this::finalMoveScore)).orElse(null);
        }

        int side = state.getSideToMove();
        int lastQueen = lastMovedQueenForSide(side);
        boolean hasDevelopingChild = root.children.stream().anyMatch(n -> isStrongOpeningMove(n.moveFromParent, side));

        Node best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Node child : root.children) {
            Move move = child.moveFromParent;
            double score = finalMoveScore(child);

            if (hasDevelopingChild && !isStrongOpeningMove(move, side)) {
                score -= 0.8;
            }
            if (move != null && move.from == lastQueen) {
                score -= 0.5;
            }
            if (move != null) {
                score += openingMoveBonus(move, side, ply);
            }

            if (score > bestScore) {
                bestScore = score;
                best = child;
            }
        }

        return best;
    }

    private boolean canExpandHere(Node node) {
        if (node.unexpandedMoves == null || node.unexpandedMoves.isEmpty()) {
            return false;
        }
        int wideningLimit = Math.max(1,
                (int) (PROGRESSIVE_WIDENING_C * Math.pow(Math.max(1, node.visitCount), PROGRESSIVE_WIDENING_ALPHA)));
        return node.children.size() < wideningLimit;
    }

    private Node bestChildByUct(Node node) {
        Node best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (Node child : node.children) {
            double value = uctValue(node, child);
            if (value > bestValue) {
                bestValue = value;
                best = child;
            }
        }
        return best == null ? node.children.get(random.nextInt(node.children.size())) : best;
    }

    private double uctValue(Node parent, Node child) {
        if (child.visitCount == 0) {
            return Double.POSITIVE_INFINITY * (1.0 + child.prior);
        }
        double exploitation = child.valueSum / child.visitCount;
        double exploration = EXPLORATION_CONSTANT * Math.sqrt(Math.log(parent.visitCount + 1.0) / child.visitCount);
        double priorBias = PRIOR_WEIGHT * child.prior / (child.visitCount + 1.0);
        return exploitation + exploration + priorBias;
    }

    private double finalMoveScore(Node child) {
        if (child.visitCount == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        double meanValue = child.valueSum / child.visitCount;
        double confidence = 0.02 * Math.log(child.visitCount + 1.0);
        return meanValue + confidence;
    }

    private void ensureUnexpandedMoves(Node node, GameState state) {
        if (node.unexpandedMoves != null) {
            return;
        }
        node.unexpandedMoves = generateCandidateMoves(state, MAX_CANDIDATE_MOVES);
    }

    private ScoredMove removeBiasedTopMove(List<ScoredMove> moves) {
        int topBand = Math.min(EXPANSION_TOP_BAND, moves.size());
        int idx = random.nextInt(topBand);
        ScoredMove chosen = moves.get(idx);
        int last = moves.size() - 1;
        moves.set(idx, moves.get(last));
        moves.remove(last);
        return chosen;
    }

    private double simulatePlayout(GameState state, int rootPlayer, int[] lastMovedQueenBySide) {
        int depth = 0;
        int startPly = countArrows(state.getBoardRef());
        while (depth < MAX_PLAYOUT_DEPTH) {
            int sideToMove = state.getSideToMove();
            if (!hasAnyLegalMove(state, sideToMove)) {
                int winner = opposite(sideToMove);
                return winner == rootPlayer ? 1.0 : 0.0;
            }

            Move move = selectPlayoutMove(state, depth, startPly + depth, lastMovedQueenBySide);
            if (move == null) {
                int winner = opposite(sideToMove);
                return winner == rootPlayer ? 1.0 : 0.0;
            }
            applyMove(state, move);
            rememberMovedQueen(lastMovedQueenBySide, sideToMove, move.to);
            depth++;
        }

        int eval = evaluateForSide(state, rootPlayer);
        return sigmoid(eval / 180.0);
    }

    private Move selectPlayoutMove(GameState state, int depth, int ply, int[] lastMovedQueenBySide) {
        int sideToMove = state.getSideToMove();
        int lastQueen = lastMovedQueenForSide(lastMovedQueenBySide, sideToMove);
        boolean openingPhase = ply < OPENING_PLY_THRESHOLD;

        double randomRate = depth < 8 ? 0.15 : PLAYOUT_RANDOM_MOVE_RATE;
        if (random.nextDouble() < randomRate) {
            return pickRandomLegalMoveReservoir(state);
        }

        ArrayList<Move> sample = sampleLegalMovesReservoir(state, PLAYOUT_TACTICAL_SAMPLE_SIZE);
        if (sample.isEmpty()) {
            return null;
        }

        Move bestMove = sample.get(0);
        int moverSide = sideToMove;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Move move : sample) {
            int movingPiece = doMove(state, move);
            double score = evaluateForSide(state, moverSide);
            state.undoMove(move.from, move.to, move.arrow, movingPiece);

            if (openingPhase) {
                score += openingMoveBonus(move, moverSide, ply);
                if (move.from == lastQueen) {
                    score -= 55.0;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    private Move pickRandomLegalMoveReservoir(GameState state) {
        int[] board = state.getBoardRef();
        int sideToMove = state.getSideToMove();

        ArrayList<Integer> queens = queensBuffer();
        ArrayList<Integer> destinations = destinationsBuffer();
        ArrayList<Integer> arrowTargets = arrowTargetsBuffer();

        collectQueenPositions(board, sideToMove, queens);

        Move chosen = null;
        int seen = 0;
        for (int queenPos : queens) {
            getReachableSquaresInto(board, queenPos, destinations);
            for (int destination : destinations) {
                getArrowTargetsAfterMoveInto(board, queenPos, destination, sideToMove, arrowTargets);
                for (int arrow : arrowTargets) {
                    seen++;
                    if (random.nextInt(seen) == 0) {
                        chosen = new Move(queenPos, destination, arrow);
                    }
                }
            }
        }
        return chosen;
    }

    private ArrayList<Move> sampleLegalMovesReservoir(GameState state, int sampleSize) {
        int[] board = state.getBoardRef();
        int sideToMove = state.getSideToMove();

        ArrayList<Integer> queens = queensBuffer();
        ArrayList<Integer> destinations = destinationsBuffer();
        ArrayList<Integer> arrowTargets = arrowTargetsBuffer();
        ArrayList<Move> sample = new ArrayList<>(Math.max(1, sampleSize));

        collectQueenPositions(board, sideToMove, queens);

        int seen = 0;
        for (int queenPos : queens) {
            getReachableSquaresInto(board, queenPos, destinations);
            for (int destination : destinations) {
                getArrowTargetsAfterMoveInto(board, queenPos, destination, sideToMove, arrowTargets);
                for (int arrow : arrowTargets) {
                    Move move = new Move(queenPos, destination, arrow);
                    seen++;
                    if (sample.size() < sampleSize) {
                        sample.add(move);
                    } else {
                        int replaceIdx = random.nextInt(seen);
                        if (replaceIdx < sampleSize) {
                            sample.set(replaceIdx, move);
                        }
                    }
                }
            }
        }

        return sample;
    }

    private List<ScoredMove> generateCandidateMoves(GameState state, int cap) {
        int[] board = state.getBoardRef();
        int sideToMove = state.getSideToMove();
        int currentPly = countArrows(board);
        PriorityQueue<ScoredMove> best = new PriorityQueue<>(Math.max(1, cap), Comparator.comparingDouble(m -> m.score));

        ArrayList<Integer> queens = queensBuffer();
        ArrayList<Integer> destinations = destinationsBuffer();
        ArrayList<Integer> arrowTargets = arrowTargetsBuffer();
        ArrayList<Integer> mobilityScratch = new ArrayList<>(32);
        int[] arrowSample = new int[MAX_ARROW_CANDIDATES_PER_DEST];

        collectQueenPositions(board, sideToMove, queens);
        for (int queenPos : queens) {
            int mobilityBefore = getReachableSquaresInto(board, queenPos, mobilityScratch).size();
            getReachableSquaresInto(board, queenPos, destinations);
            for (int destination : destinations) {
                getArrowTargetsAfterMoveInto(board, queenPos, destination, sideToMove, arrowTargets);

                if (arrowTargets.isEmpty()) {
                    continue;
                }

                int mobilityAfter = getReachableSquaresWithMoveInto(
                        board, destination, queenPos, destination, sideToMove, mobilityScratch).size();

                int sampled = sampleArrows(arrowTargets, arrowSample);
                for (int i = 0; i < sampled; i++) {
                    int arrow = arrowSample[i];
                    Move move = new Move(queenPos, destination, arrow);
                    double score = quickMoveScore(
                            sideToMove,
                            queenPos,
                            destination,
                            arrow,
                            mobilityBefore,
                            mobilityAfter,
                            arrowTargets.size(),
                            currentPly);
                    offerCandidate(best, cap, new ScoredMove(move, score));
                }
            }
        }

        ArrayList<ScoredMove> candidates = new ArrayList<>(best);
        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        return candidates;
    }

    private int sampleArrows(ArrayList<Integer> arrowTargets, int[] out) {
        if (arrowTargets.isEmpty()) {
            return 0;
        }

        int count = 0;
        int first = arrowTargets.get(0);
        out[count++] = first;

        if (arrowTargets.size() > 1 && count < out.length) {
            int mid = arrowTargets.get(arrowTargets.size() / 2);
            if (mid != first) {
                out[count++] = mid;
            }
        }

        if (arrowTargets.size() > 2 && count < out.length) {
            int rand = arrowTargets.get(random.nextInt(arrowTargets.size()));
            boolean duplicate = false;
            for (int i = 0; i < count; i++) {
                if (out[i] == rand) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                out[count++] = rand;
            }
        }

        return count;
    }

    private double quickMoveScore(
            int sideToMove,
            int from,
            int destination,
            int arrow,
            int mobilityBefore,
            int mobilityAfter,
            int arrowFanout,
            int ply) {
        int territoryGain = mobilityAfter - mobilityBefore;
        int centerProgress = manhattanFromCenter(from) - manhattanFromCenter(destination);
        int forwardProgress = rowProgress(sideToMove, from, destination);

        double centerPenaltyDest = manhattanFromCenter(destination);
        double centerPenaltyArrow = manhattanFromCenter(arrow);
        double base = 1.50 * mobilityAfter
                + 0.42 * territoryGain
                + 0.70 * centerProgress
                + 0.48 * forwardProgress
                + 0.15 * arrowFanout
                - 0.18 * centerPenaltyDest
                - 0.08 * centerPenaltyArrow
                + random.nextDouble() * 0.01;

        if (ply < OPENING_PLY_THRESHOLD) {
            base += openingMoveBonus(new Move(from, destination, arrow), sideToMove, ply);
            int lastQueen = lastMovedQueenForSide(sideToMove);
            if (from == lastQueen) {
                base -= 4.0;
            }
        }

        return base;
    }

    private void offerCandidate(PriorityQueue<ScoredMove> pq, int cap, ScoredMove candidate) {
        if (pq.size() < cap) {
            pq.offer(candidate);
            return;
        }
        ScoredMove worst = pq.peek();
        if (worst != null && candidate.score > worst.score) {
            pq.poll();
            pq.offer(candidate);
        }
    }

    private int doMove(GameState state, Move move) {
        int[] board = state.getBoardRef();
        int movingPiece = board[move.from];
        board[move.from] = GameState.EMPTY;
        board[move.to] = movingPiece;
        board[move.arrow] = GameState.ARROW;
        state.setSideToMove(opposite(state.getSideToMove()));
        return movingPiece;
    }

    private static int manhattanFromCenter(int index) {
        int row = index / GameState.BOARD_SIZE;
        int col = index % GameState.BOARD_SIZE;
        return Math.abs((GameState.BOARD_SIZE - 1) - 2 * row) + Math.abs((GameState.BOARD_SIZE - 1) - 2 * col);
    }

    private static int rowProgress(int sideToMove, int from, int to) {
        int fromRow = from / GameState.BOARD_SIZE;
        int toRow = to / GameState.BOARD_SIZE;
        if (sideToMove == GameState.BLACK) {
            return toRow - fromRow;
        }
        return fromRow - toRow;
    }

    private int evaluateForSide(GameState state, int side) {
        int opp = opposite(side);
        return heuristic.evaluate(state, side) - heuristic.evaluate(state, opp);
    }

    private double openingMoveBonus(Move move, int side, int ply) {
        if (move == null || ply >= OPENING_PLY_THRESHOLD) {
            return 0.0;
        }

        int from = move.from;
        int to = move.to;

        int homeDistanceGain = homeDistance(side, from) - homeDistance(side, to);
        int centerGain = manhattanFromCenter(from) - manhattanFromCenter(to);
        int forward = rowProgress(side, from, to);
        boolean leavesHomeBand = inHomeBand(side, from) && !inHomeBand(side, to);

        double bonus = 1.8 * homeDistanceGain + 1.1 * centerGain + 0.7 * forward;
        if (leavesHomeBand) {
            bonus += 2.6;
        }
        if (isBackwardsInOpening(side, from, to)) {
            bonus -= 2.2;
        }
        return bonus;
    }

    private boolean isStrongOpeningMove(Move move, int side) {
        if (move == null) {
            return false;
        }
        int homeGain = homeDistance(side, move.from) - homeDistance(side, move.to);
        int centerGain = manhattanFromCenter(move.from) - manhattanFromCenter(move.to);
        return homeGain >= 1 || centerGain >= 2 || (inHomeBand(side, move.from) && !inHomeBand(side, move.to));
    }

    private static boolean inHomeBand(int side, int pos) {
        int row = pos / GameState.BOARD_SIZE;
        if (side == GameState.BLACK) {
            return row <= 3;
        }
        return row >= 6;
    }

    private static int homeDistance(int side, int pos) {
        int row = pos / GameState.BOARD_SIZE;
        if (side == GameState.BLACK) {
            return row;
        }
        return (GameState.BOARD_SIZE - 1) - row;
    }

    private static boolean isBackwardsInOpening(int side, int from, int to) {
        int fromRow = from / GameState.BOARD_SIZE;
        int toRow = to / GameState.BOARD_SIZE;
        if (side == GameState.BLACK) {
            return toRow < fromRow;
        }
        return toRow > fromRow;
    }

    private static int countArrows(int[] board) {
        int arrows = 0;
        for (int cell : board) {
            if (cell == GameState.ARROW) {
                arrows++;
            }
        }
        return arrows;
    }

    private int lastMovedQueenForSide(int side) {
        return side == GameState.BLACK ? lastMovedQueenBlack : lastMovedQueenWhite;
    }

    private static int lastMovedQueenForSide(int[] bySide, int side) {
        return side == GameState.BLACK ? bySide[0] : bySide[1];
    }

    private void rememberMovedQueen(int side, int queenPos) {
        if (side == GameState.BLACK) {
            lastMovedQueenBlack = queenPos;
        } else {
            lastMovedQueenWhite = queenPos;
        }
    }

    private static void rememberMovedQueen(int[] bySide, int side, int queenPos) {
        if (side == GameState.BLACK) {
            bySide[0] = queenPos;
        } else {
            bySide[1] = queenPos;
        }
    }

    private void applyMove(GameState state, Move move) {
        state.applyMove(move);
    }

    private void warnNodeCap() {
        if (!warnedNodeCap) {
            System.err.println("Warning: MCTSv2 node cap reached (" + maxNodes + ")");
            warnedNodeCap = true;
        }
    }

    private static int opposite(int side) {
        return side == GameState.BLACK ? GameState.WHITE : GameState.BLACK;
    }

    private static double sigmoid(double x) {
        if (x > 35.0) {
            return 1.0;
        }
        if (x < -35.0) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
