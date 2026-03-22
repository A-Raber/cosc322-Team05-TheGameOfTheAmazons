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
    private static final long MAIN_SEARCH_TIME_BUDGET_MS = 27_000L;
    private static final long PONDER_TIME_BUDGET_MS = 27_000L;
    private static final int PONDER_OPPONENT_CANDIDATE_CAP = 64;
    private static final int PONDER_BRANCH_COUNT = 3;
    private static final long PONDER_MIN_BRANCH_TIME_MS = 3_500L;
    private static final int PONDER_REFINEMENT_TOP = 14;
    private static final int TACTICAL_WIN_CHECK_CAP = 96;
    private static final int BLUNDER_GUARD_RESPONSE_CHECK_CAP = 72;
    private static final double FORMATION_WEIGHT_OPENING = 1.55;
    private static final double FORMATION_WEIGHT_MIDGAME = 0.90;
    private static final double OPPONENT_BLOCK_WEIGHT_OPENING = 1.05;
    private static final double OPPONENT_BLOCK_WEIGHT_MIDGAME = 0.60;

    private final Random random = new Random();
    private final PhaseAwareHeuristic heuristic = new PhaseAwareHeuristic();

    private final int maxNodes;
    private int lastMovedQueenBlack = -1;
    private int lastMovedQueenWhite = -1;
    private volatile Thread ponderThread;
    private volatile long ponderGeneration;
    private volatile List<PonderResult> completedPonders = new ArrayList<>();
    private volatile Move matchedPonderMove;
    private volatile long matchedPonderStateHash;

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

    private static final class PonderResult {
        final long generation;
        final Move predictedOpponentMove;
        final Move bestResponse;
        final long responseStateHash;
        final double predictionWeight;

        PonderResult(long generation, Move predictedOpponentMove, Move bestResponse, long responseStateHash,
                double predictionWeight) {
            this.generation = generation;
            this.predictedOpponentMove = predictedOpponentMove;
            this.bestResponse = bestResponse;
            this.responseStateHash = responseStateHash;
            this.predictionWeight = predictionWeight;
        }
    }

    private static final class PredictedBranch {
        final Move move;
        final double weight;

        PredictedBranch(Move move, double weight) {
            this.move = move;
            this.weight = Math.max(0.001, weight);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ArrayList<Integer>[] generateMove(GameState gameState) {
        requestStopPondering();

        Move bestMove = consumeMatchedPonderMove(gameState);
        int rootPlayer = gameState.getSideToMove();
        if (bestMove == null) {
            bestMove = chooseMoveInternal(gameState, true, MAIN_SEARCH_TIME_BUDGET_MS);
        } else {
            System.out.println("Predicted");
            rememberMovedQueen(rootPlayer, bestMove.to);
        }

        if (bestMove != null && !isValidMove(bestMove)) {
            System.err.println("Warning: MCTSv2 produced invalid move indices, using random legal fallback: "
                    + bestMove.from + " -> " + bestMove.to + " arrow " + bestMove.arrow);
            bestMove = pickRandomLegalMoveReservoir(gameState);
        }

        if (bestMove == null) {
            return null;
        }

        return new ArrayList[] {
                toServerPosition(bestMove.from),
                toServerPosition(bestMove.to),
                toServerPosition(bestMove.arrow)
        };
    }

    @Override
    public void onOwnMovePlayed(GameState stateAfterOwnMove, Move ownMove) {
        if (stateAfterOwnMove == null || ownMove == null) {
            return;
        }
        startPondering(stateAfterOwnMove);
    }

    @Override
    public void onOpponentMoveObserved(GameState stateBeforeOpponentMove, Move opponentMove) {
        requestStopPondering();

        matchedPonderMove = null;
        matchedPonderStateHash = 0L;

        if (stateBeforeOpponentMove == null || opponentMove == null) {
            completedPonders = new ArrayList<>();
            return;
        }

        List<PonderResult> results = completedPonders;
        completedPonders = new ArrayList<>();
        if (results == null || results.isEmpty()) {
            return;
        }

        GameState afterOpponentMove = stateBeforeOpponentMove.copy();
        applyMove(afterOpponentMove, opponentMove);
        long actualHash = computeStateHash(afterOpponentMove);

        PonderResult matched = null;
        for (PonderResult result : results) {
            if (!sameMove(result.predictedOpponentMove, opponentMove)) {
                continue;
            }
            if (actualHash != result.responseStateHash) {
                continue;
            }
            if (matched == null || result.predictionWeight > matched.predictionWeight) {
                matched = result;
            }
        }

        if (matched != null) {
            matchedPonderMove = matched.bestResponse;
            matchedPonderStateHash = actualHash;
        }
    }

    @Override
    public void shutdown() {
        requestStopPondering();
        completedPonders = new ArrayList<>();
        matchedPonderMove = null;
        matchedPonderStateHash = 0L;
    }

    private Move chooseMoveInternal(GameState gameState, boolean rememberMoveMemory, long timeBudgetMs) {
        long endTime = System.currentTimeMillis() + Math.max(1L, timeBudgetMs);

        int rootPlayer = gameState.getSideToMove();

        // Tactical shortcut: if we can end the game immediately, play that move.
        Move immediateWin = findImmediateWinningMove(gameState, rootPlayer, TACTICAL_WIN_CHECK_CAP);
        if (immediateWin != null) {
            if (rememberMoveMemory) {
                rememberMovedQueen(rootPlayer, immediateWin.to);
            }
            return immediateWin;
        }

        Node root = new Node(null, 0.5);
        int rootPly = countArrows(gameState.getBoardRef());
        boolean openingPhase = rootPly < OPENING_PLY_THRESHOLD;

        int nodeCount = 1;
        boolean warnedNodeCap = false;
        int iterations = 0;

        while (System.currentTimeMillis() < endTime && iterations < MAX_ITERATIONS && nodeCount < maxNodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
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
                    if (!warnedNodeCap) {
                        System.err.println("Warning: MCTSv2 node cap reached (" + maxNodes + ")");
                        warnedNodeCap = true;
                    }
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
        if (rememberMoveMemory) {
            rememberMovedQueen(rootPlayer, bestMove.to);
        }
        return bestMove;
    }

    private void startPondering(GameState stateAfterOwnMove) {
        requestStopPondering();
        completedPonders = new ArrayList<>();
        matchedPonderMove = null;
        matchedPonderStateHash = 0L;

        final long generation = ++ponderGeneration;
        final GameState ponderRoot = stateAfterOwnMove.copy();
        Thread worker = new Thread(() -> runPonderingTask(ponderRoot, generation), "MCTSv2-Ponder");
        worker.setDaemon(true);
        ponderThread = worker;
        worker.start();
    }

    private void runPonderingTask(GameState stateAfterOwnMove, long generation) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        List<PredictedBranch> branches = predictLikelyOpponentMoves(stateAfterOwnMove);
        if (branches.isEmpty() || Thread.currentThread().isInterrupted()) {
            return;
        }

        double totalWeight = 0.0;
        for (PredictedBranch branch : branches) {
            totalWeight += branch.weight;
        }
        long remainingMs = PONDER_TIME_BUDGET_MS;
        ArrayList<PonderResult> computed = new ArrayList<>(branches.size());

        for (int i = 0; i < branches.size(); i++) {
            if (Thread.currentThread().isInterrupted() || generation != ponderGeneration || remainingMs <= 0) {
                break;
            }

            PredictedBranch branch = branches.get(i);
            long branchBudget;
            if (i == branches.size() - 1) {
                branchBudget = remainingMs;
            } else {
                double fraction = branch.weight / Math.max(0.001, totalWeight);
                long weighted = Math.round(PONDER_TIME_BUDGET_MS * fraction);
                branchBudget = Math.max(PONDER_MIN_BRANCH_TIME_MS, weighted);
                branchBudget = Math.min(branchBudget, remainingMs);
            }

            GameState responseState = stateAfterOwnMove.copy();
            applyMove(responseState, branch.move);
            long responseStateHash = computeStateHash(responseState);
            Move bestResponse = chooseMoveInternal(responseState, false, branchBudget);
            if (bestResponse != null && !Thread.currentThread().isInterrupted()) {
                computed.add(new PonderResult(generation, branch.move, bestResponse, responseStateHash, branch.weight));
                completedPonders = new ArrayList<>(computed);
            }

            remainingMs -= branchBudget;
            totalWeight -= branch.weight;
        }

        if (generation == ponderGeneration && !computed.isEmpty()) {
            completedPonders = new ArrayList<>(computed);
        }
    }

    private List<PredictedBranch> predictLikelyOpponentMoves(GameState state) {
        int sideToMove = state.getSideToMove();

        // If opponent has an immediate win, predict that first.
        Move tacticalWin = findImmediateWinningMove(state, sideToMove, TACTICAL_WIN_CHECK_CAP);
        if (tacticalWin != null) {
            ArrayList<PredictedBranch> forced = new ArrayList<>(1);
            forced.add(new PredictedBranch(tacticalWin, 1.0));
            return forced;
        }

        List<ScoredMove> candidates = generateCandidateMoves(state, PONDER_OPPONENT_CANDIDATE_CAP);
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }

        int evalTop = Math.min(PONDER_REFINEMENT_TOP, candidates.size());
        ArrayList<ScoredMove> refined = new ArrayList<>(evalTop);

        for (int i = 0; i < evalTop; i++) {
            ScoredMove candidate = candidates.get(i);
            int movingPiece = doMove(state, candidate.move);
            double tactical = evaluateForSide(state, sideToMove);
            state.undoMove(candidate.move.from, candidate.move.to, candidate.move.arrow, movingPiece);

            double blend = candidate.score + 0.020 * tactical;
            refined.add(new ScoredMove(candidate.move, blend));
        }

        refined.sort((a, b) -> Double.compare(b.score, a.score));
        int branchCount = Math.min(PONDER_BRANCH_COUNT, refined.size());
        ArrayList<PredictedBranch> branches = new ArrayList<>(branchCount);
        for (int i = 0; i < branchCount; i++) {
            ScoredMove pick = refined.get(i);
            branches.add(new PredictedBranch(pick.move, pick.prior));
        }
        return branches;
    }

    private Move findImmediateWinningMove(GameState state, int movingSide, int candidateCap) {
        List<ScoredMove> candidates = generateCandidateMoves(state, candidateCap);
        int opponent = opposite(movingSide);
        for (ScoredMove candidate : candidates) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }

            Move move = candidate.move;
            int movingPiece = doMove(state, move);
            boolean opponentHasMove = hasAnyLegalMove(state, opponent);
            state.undoMove(move.from, move.to, move.arrow, movingPiece);

            if (!opponentHasMove) {
                return move;
            }
        }
        return null;
    }

    private Move consumeMatchedPonderMove(GameState gameState) {
        Move cached = matchedPonderMove;
        if (cached == null) {
            return null;
        }
        long expectedHash = matchedPonderStateHash;
        matchedPonderMove = null;
        matchedPonderStateHash = 0L;

        long actualHash = computeStateHash(gameState);
        if (actualHash != expectedHash) {
            return null;
        }
        return cached;
    }

    private void requestStopPondering() {
        Thread worker = ponderThread;
        if (worker != null) {
            worker.interrupt();
        }
        ponderThread = null;
    }

    private static boolean sameMove(Move a, Move b) {
        return a != null && b != null && a.from == b.from && a.to == b.to && a.arrow == b.arrow;
    }

    private static long computeStateHash(GameState state) {
        int[] board = state.getBoardRef();
        long h = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < board.length; i++) {
            long v = (long) (board[i] + 1) * (i + 0x100L);
            h ^= v + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
        }
        h ^= (state.getSideToMove() * 0xBF58476D1CE4E5B9L);
        return h;
    }

    private Node selectBestRootChild(Node root, GameState state, boolean openingPhase, int ply) {
        if (root.children.isEmpty()) {
            return null;
        }

        int side = state.getSideToMove();
        int lastQueen = lastMovedQueenForSide(side);
        boolean hasDevelopingChild = root.children.stream().anyMatch(n -> isStrongOpeningMove(n.moveFromParent, side));
        int[] board = state.getBoardRef();

        GameState rootScratch = state.copy();

        Node bestSafe = null;
        double bestSafeScore = Double.NEGATIVE_INFINITY;
        Node best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Node child : root.children) {
            Move move = child.moveFromParent;
            double score = finalMoveScore(child);

            if (openingPhase) {
                if (hasDevelopingChild && !isStrongOpeningMove(move, side)) {
                    score -= 0.8;
                }
                if (move != null && move.from == lastQueen) {
                    score -= 0.5;
                }
                if (move != null) {
                    score += openingMoveBonus(move, side, ply);
                    score += FORMATION_WEIGHT_OPENING * localFormationAfterMove(board, side, move.from, move.to);
                }
            } else if (move != null) {
                score += FORMATION_WEIGHT_MIDGAME * localFormationAfterMove(board, side, move.from, move.to);
            }

            if (score > bestScore) {
                bestScore = score;
                best = child;
            }

            if (move == null) {
                continue;
            }
            if (allowsImmediateOpponentWin(rootScratch, move)) {
                continue;
            }
            if (score > bestSafeScore) {
                bestSafeScore = score;
                bestSafe = child;
            }
        }

        return bestSafe != null ? bestSafe : best;
    }

    private boolean allowsImmediateOpponentWin(GameState state, Move move) {
        int movingPiece = doMove(state, move);
        int opponentSide = state.getSideToMove();
        Move opponentWinningReply = findImmediateWinningMove(state, opponentSide, BLUNDER_GUARD_RESPONSE_CHECK_CAP);
        state.undoMove(move.from, move.to, move.arrow, movingPiece);
        return opponentWinningReply != null;
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
            double formation = localFormationAt(state.getBoardRef(), moverSide, move.to);
            state.undoMove(move.from, move.to, move.arrow, movingPiece);

            if (openingPhase) {
                score += openingMoveBonus(move, moverSide, ply);
                score += 70.0 * formation;
                if (move.from == lastQueen) {
                    score -= 55.0;
                }
            } else {
                score += 36.0 * formation;
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
        int opponentSide = opposite(sideToMove);
        int currentPly = countArrows(board);
        int opponentMobilityBaseline = estimateTotalMobility(board, opponentSide);
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

                int sampled = sampleArrows(board, sideToMove, queenPos, destination, arrowTargets, arrowSample);
                for (int i = 0; i < sampled; i++) {
                    int arrow = arrowSample[i];
                    int opponentMobilityAfter = estimateOpponentMobilityAfterMove(
                        board,
                        opponentSide,
                        queenPos,
                        destination,
                        sideToMove,
                        arrow);
                    Move move = new Move(queenPos, destination, arrow);
                    double score = quickMoveScore(
                            board,
                            sideToMove,
                            queenPos,
                            destination,
                            arrow,
                            mobilityBefore,
                            mobilityAfter,
                            arrowTargets.size(),
                            opponentMobilityBaseline,
                            opponentMobilityAfter,
                            currentPly);
                    offerCandidate(best, cap, new ScoredMove(move, score));
                }
            }
        }

        ArrayList<ScoredMove> candidates = new ArrayList<>(best);
        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        return candidates;
    }

    private int sampleArrows(int[] board, int sideToMove, int from, int destination, ArrayList<Integer> arrowTargets, int[] out) {
        if (arrowTargets.isEmpty()) {
            return 0;
        }

        int take = Math.min(out.length, arrowTargets.size());
        int[] bestArrow = new int[take];
        double[] bestScore = new double[take];
        for (int i = 0; i < take; i++) {
            bestArrow[i] = -1;
            bestScore[i] = Double.NEGATIVE_INFINITY;
        }

        for (int arrow : arrowTargets) {
            double score = arrowPlacementScore(board, sideToMove, from, destination, arrow);
            for (int slot = 0; slot < take; slot++) {
                if (score <= bestScore[slot]) {
                    continue;
                }
                for (int shift = take - 1; shift > slot; shift--) {
                    bestArrow[shift] = bestArrow[shift - 1];
                    bestScore[shift] = bestScore[shift - 1];
                }
                bestArrow[slot] = arrow;
                bestScore[slot] = score;
                break;
            }
        }

        int count = 0;
        for (int i = 0; i < take; i++) {
            if (bestArrow[i] >= 0) {
                out[count++] = bestArrow[i];
            }
        }
        return count;
    }

    private double quickMoveScore(
            int[] board,
            int sideToMove,
            int from,
            int destination,
            int arrow,
            int mobilityBefore,
            int mobilityAfter,
            int arrowFanout,
            int opponentMobilityBefore,
            int opponentMobilityAfter,
            int ply) {
        int territoryGain = mobilityAfter - mobilityBefore;
        int opponentMobilityDelta = opponentMobilityBefore - opponentMobilityAfter;
        int centerProgress = manhattanFromCenter(from) - manhattanFromCenter(destination);
        int forwardProgress = rowProgress(sideToMove, from, destination);
        double formation = localFormationAfterMove(board, sideToMove, from, destination);
        int selfLibertiesAfterShot = countReachableWithVirtualMoveAndArrow(board, destination, from, destination, sideToMove,
                arrow);
        int arrowDistanceToSelf = chebyshevDistance(destination, arrow);

        double centerPenaltyDest = manhattanFromCenter(destination);
        double centerPenaltyArrow = manhattanFromCenter(arrow);
        double formationWeight = ply < OPENING_PLY_THRESHOLD ? FORMATION_WEIGHT_OPENING : FORMATION_WEIGHT_MIDGAME;
        double opponentBlockWeight = ply < OPENING_PLY_THRESHOLD ? OPPONENT_BLOCK_WEIGHT_OPENING : OPPONENT_BLOCK_WEIGHT_MIDGAME;
        double base = 1.50 * mobilityAfter
                + 0.42 * territoryGain
                + opponentBlockWeight * opponentMobilityDelta
                + 0.70 * centerProgress
                + 0.48 * forwardProgress
                + 0.15 * arrowFanout
                - 0.18 * centerPenaltyDest
                - 0.08 * centerPenaltyArrow
                + formationWeight * formation
                + random.nextDouble() * 0.01;

        if (selfLibertiesAfterShot <= 4) {
            base -= 2.6 * (5 - selfLibertiesAfterShot);
        } else {
            base += 0.12 * Math.min(10, selfLibertiesAfterShot - 4);
        }

        if (arrowDistanceToSelf <= 1) {
            base -= 2.2;
        } else if (arrowDistanceToSelf == 2) {
            base -= 0.6;
        }

        if (ply < OPENING_PLY_THRESHOLD) {
            base += openingMoveBonus(new Move(from, destination, arrow), sideToMove, ply);
            int lastQueen = lastMovedQueenForSide(sideToMove);
            if (from == lastQueen) {
                base -= 4.0;
            }
        }

        return base;
    }

    private double arrowPlacementScore(int[] board, int sideToMove, int from, int destination, int arrow) {
        int selfDist = chebyshevDistance(destination, arrow);
        int nearestOpponentDist = nearestQueenDistance(board, opposite(sideToMove), arrow);
        int nearestFriendlyDist = nearestQueenDistanceWithMove(board, sideToMove, from, destination, arrow);
        int selfLibertiesAfterShot = countReachableWithVirtualMoveAndArrow(board, destination, from, destination, sideToMove,
                arrow);

        double score = 0.0;
        if (selfDist <= 1) {
            score -= 4.0;
        } else if (selfDist == 2) {
            score -= 1.5;
        } else {
            score += 0.35;
        }

        score += 1.4 * Math.max(0, 8 - nearestOpponentDist);
        score -= 0.85 * Math.max(0, 3 - nearestFriendlyDist);
        score += 0.18 * Math.min(14, selfLibertiesAfterShot);
        return score;
    }

    private int estimateOpponentMobilityAfterMove(
            int[] board,
            int opponentSide,
            int from,
            int destination,
            int movingSide,
            int arrow) {
        int mobility = 0;
        for (int i = 0; i < GameState.BOARD_CELLS; i++) {
            if (board[i] != opponentSide || i == arrow) {
                continue;
            }
            mobility += countReachableWithVirtualMoveAndArrow(board, i, from, destination, movingSide, arrow);
        }
        return mobility;
    }

    private int estimateTotalMobility(int[] board, int side) {
        int mobility = 0;
        ArrayList<Integer> scratch = new ArrayList<>(32);
        for (int i = 0; i < GameState.BOARD_CELLS; i++) {
            if (board[i] != side) {
                continue;
            }
            mobility += getReachableSquaresInto(board, i, scratch).size();
        }
        return mobility;
    }

    private int countReachableWithVirtualMoveAndArrow(
            int[] board,
            int pos,
            int from,
            int destination,
            int movingSide,
            int arrow) {
        int count = 0;
        for (int offset : DIRECTION_OFFSETS) {
            int current = pos;
            while (true) {
                int next = current + offset;
                if (!isInsideBoard(next) || crossesRowBoundary(current, next)) {
                    break;
                }

                int cellValue = board[next];
                if (next == from) {
                    cellValue = GameState.EMPTY;
                } else if (next == destination) {
                    cellValue = movingSide;
                } else if (next == arrow) {
                    cellValue = GameState.ARROW;
                }

                if (cellValue != GameState.EMPTY) {
                    break;
                }

                count++;
                current = next;
            }
        }
        return count;
    }

    private static int chebyshevDistance(int a, int b) {
        int aRow = a / GameState.BOARD_SIZE;
        int aCol = a % GameState.BOARD_SIZE;
        int bRow = b / GameState.BOARD_SIZE;
        int bCol = b % GameState.BOARD_SIZE;
        return Math.max(Math.abs(aRow - bRow), Math.abs(aCol - bCol));
    }

    private int nearestQueenDistance(int[] board, int side, int fromPos) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < GameState.BOARD_CELLS; i++) {
            if (board[i] == side) {
                min = Math.min(min, chebyshevDistance(fromPos, i));
            }
        }
        return min == Integer.MAX_VALUE ? 9 : min;
    }

    private int nearestQueenDistanceWithMove(int[] board, int side, int from, int destination, int fromPos) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < GameState.BOARD_CELLS; i++) {
            int piece = board[i];
            if (i == from) {
                piece = GameState.EMPTY;
            } else if (i == destination) {
                piece = side;
            }
            if (piece == side && i != destination) {
                min = Math.min(min, chebyshevDistance(fromPos, i));
            }
        }
        return min == Integer.MAX_VALUE ? 9 : min;
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
        if (!isValidMove(move)) {
            throw new IllegalArgumentException("Invalid move indices: "
                    + move.from + " -> " + move.to + " arrow " + move.arrow);
        }
        int[] board = state.getBoardRef();
        int movingPiece = board[move.from];
        board[move.from] = GameState.EMPTY;
        board[move.to] = movingPiece;
        board[move.arrow] = GameState.ARROW;
        state.setSideToMove(opposite(state.getSideToMove()));
        return movingPiece;
    }

    private static boolean isValidMove(Move move) {
        return move != null && isValidIndex(move.from) && isValidIndex(move.to) && isValidIndex(move.arrow);
    }

    private static boolean isValidIndex(int idx) {
        return idx >= 0 && idx < GameState.BOARD_CELLS;
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

    private double localFormationAfterMove(int[] board, int side, int from, int to) {
        int toRow = to / GameState.BOARD_SIZE;
        int toCol = to % GameState.BOARD_SIZE;

        int adjacentFriendlies = 0;
        int nearbyFriendlies = 0;
        int minDistance = Integer.MAX_VALUE;

        for (int idx = 0; idx < GameState.BOARD_CELLS; idx++) {
            if (idx == from || board[idx] != side) {
                continue;
            }

            int row = idx / GameState.BOARD_SIZE;
            int col = idx % GameState.BOARD_SIZE;
            int dr = Math.abs(toRow - row);
            int dc = Math.abs(toCol - col);
            int chebyshev = Math.max(dr, dc);
            int manhattan = dr + dc;

            if (chebyshev <= 1) {
                adjacentFriendlies++;
            }
            if (manhattan <= 3) {
                nearbyFriendlies++;
            }
            if (manhattan < minDistance) {
                minDistance = manhattan;
            }
        }

        return formationScoreFromDistances(adjacentFriendlies, nearbyFriendlies, minDistance);
    }

    private double localFormationAt(int[] board, int side, int queenPos) {
        int qRow = queenPos / GameState.BOARD_SIZE;
        int qCol = queenPos % GameState.BOARD_SIZE;

        int adjacentFriendlies = 0;
        int nearbyFriendlies = 0;
        int minDistance = Integer.MAX_VALUE;

        for (int idx = 0; idx < GameState.BOARD_CELLS; idx++) {
            if (idx == queenPos || board[idx] != side) {
                continue;
            }

            int row = idx / GameState.BOARD_SIZE;
            int col = idx % GameState.BOARD_SIZE;
            int dr = Math.abs(qRow - row);
            int dc = Math.abs(qCol - col);
            int chebyshev = Math.max(dr, dc);
            int manhattan = dr + dc;

            if (chebyshev <= 1) {
                adjacentFriendlies++;
            }
            if (manhattan <= 3) {
                nearbyFriendlies++;
            }
            if (manhattan < minDistance) {
                minDistance = manhattan;
            }
        }

        return formationScoreFromDistances(adjacentFriendlies, nearbyFriendlies, minDistance);
    }

    private double formationScoreFromDistances(int adjacentFriendlies, int nearbyFriendlies, int minDistance) {
        double score = 0.0;

        score -= 2.80 * adjacentFriendlies;
        score -= 0.90 * Math.max(0, nearbyFriendlies - adjacentFriendlies);

        if (minDistance == Integer.MAX_VALUE) {
            return score;
        }

        if (minDistance <= 1) {
            score -= 2.0;
        } else if (minDistance == 2) {
            score -= 0.8;
        } else if (minDistance <= 4) {
            score += 1.0;
        } else if (minDistance <= 6) {
            score += 0.4;
        }

        return score;
    }

    private int evaluateForSide(GameState state, int side) {
        return heuristic.evaluate(state, side);
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
