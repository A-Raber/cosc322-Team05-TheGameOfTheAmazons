package ubc.cosc322.engine.mcts.v2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import ubc.cosc322.engine.AbstractMoveGenerator;
import ubc.cosc322.heuristic.PhaseAwareHeuristic;
import ubc.cosc322.model.GameState;
import ubc.cosc322.model.Move;
import ubc.cosc322.util.Timer;

/**
 * MCTSv2 is memory-optimized relative to v1:
 * - Node does not store a full GameState copy.
 * - Expansion is lazy (one child per visit), not full fan-out per visit.
 * - Playout move selection uses reservoir sampling (no full move list allocation).
 */
public class MCTSv2 extends AbstractMoveGenerator {

    private static final double EXPLORATION_CONSTANT = Math.sqrt(2.0);
    private static final int DEFAULT_MAX_NODES = 1_200_000;
    private static final int MAX_PLAYOUT_DEPTH = 24;
    private static final int MAX_ITERATIONS = 120_000;

    private final Random random = new Random();
    private final PhaseAwareHeuristic heuristic = new PhaseAwareHeuristic();

    private final int maxNodes;
    private int nodeCount;
    private boolean warnedNodeCap;

    public MCTSv2() {
        this(DEFAULT_MAX_NODES);
    }

    public MCTSv2(int maxNodes) {
        this.maxNodes = Math.max(1, maxNodes);
    }

    private static final class Node {
        final Move moveFromParent;
        final ArrayList<Node> children = new ArrayList<>();

        List<Move> unexpandedMoves;
        int visitCount;
        double valueSum;

        Node(Move moveFromParent) {
            this.moveFromParent = moveFromParent;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ArrayList<Integer>[] generateMove(GameState gameState) {
        Timer timer = new Timer();
        timer.start();

        Node root = new Node(null);
        int rootPlayer = gameState.getSideToMove();

        nodeCount = 1;
        warnedNodeCap = false;
        int iterations = 0;

        while (!timer.timeUp() && iterations < MAX_ITERATIONS && nodeCount < maxNodes) {
            GameState rolloutState = gameState.copy();
            ArrayList<Node> path = new ArrayList<>(32);
            Node node = root;
            path.add(node);

            // 1) Selection: follow UCT over expanded children.
            while (!node.children.isEmpty() && isFullyExpanded(node)) {
                node = bestChildByUct(node);
                applyMove(rolloutState, node.moveFromParent);
                path.add(node);
            }

            // 2) Expansion: add a single child lazily.
            if (hasAnyLegalMove(rolloutState, rolloutState.getSideToMove())) {
                ensureUnexpandedMoves(node, rolloutState);
                if (node.unexpandedMoves != null && !node.unexpandedMoves.isEmpty()) {
                    if (nodeCount >= maxNodes) {
                        warnNodeCap();
                    } else {
                        Move move = removeRandomMove(node.unexpandedMoves);
                        Node child = new Node(move);
                        node.children.add(child);
                        nodeCount++;

                        applyMove(rolloutState, move);
                        node = child;
                        path.add(node);
                    }
                }
            }

            // 3) Simulation + 4) Backpropagation.
            double result = simulatePlayout(rolloutState, rootPlayer);
            for (Node visited : path) {
                visited.visitCount++;
                visited.valueSum += result;
            }

            iterations++;
        }

        Node bestChild = root.children.stream()
                .max(Comparator.comparingInt(n -> n.visitCount))
                .orElse(null);
        if (bestChild == null || bestChild.moveFromParent == null) {
            return null;
        }

        Move bestMove = bestChild.moveFromParent;
        return new ArrayList[] {
                toServerPosition(bestMove.from),
                toServerPosition(bestMove.to),
                toServerPosition(bestMove.arrow)
        };
    }

    private boolean isFullyExpanded(Node node) {
        return node.unexpandedMoves != null && node.unexpandedMoves.isEmpty();
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
            return Double.POSITIVE_INFINITY;
        }
        double exploitation = child.valueSum / child.visitCount;
        double exploration = EXPLORATION_CONSTANT * Math.sqrt(Math.log(parent.visitCount + 1.0) / child.visitCount);
        return exploitation + exploration;
    }

    private void ensureUnexpandedMoves(Node node, GameState state) {
        if (node.unexpandedMoves != null) {
            return;
        }
        node.unexpandedMoves = generateAllLegalMoves(state);
    }

    private Move removeRandomMove(List<Move> moves) {
        int idx = random.nextInt(moves.size());
        int last = moves.size() - 1;
        Move chosen = moves.get(idx);
        moves.set(idx, moves.get(last));
        moves.remove(last);
        return chosen;
    }

    private double simulatePlayout(GameState state, int rootPlayer) {
        int depth = 0;
        while (depth < MAX_PLAYOUT_DEPTH) {
            int sideToMove = state.getSideToMove();
            if (!hasAnyLegalMove(state, sideToMove)) {
                int winner = opposite(sideToMove);
                return winner == rootPlayer ? 1.0 : 0.0;
            }

            Move move = pickRandomLegalMoveReservoir(state);
            if (move == null) {
                int winner = opposite(sideToMove);
                return winner == rootPlayer ? 1.0 : 0.0;
            }
            applyMove(state, move);
            depth++;
        }

        int eval = heuristic.evaluate(state, rootPlayer);
        return sigmoid(eval / 180.0);
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

    private List<Move> generateAllLegalMoves(GameState state) {
        int[] board = state.getBoardRef();
        int sideToMove = state.getSideToMove();
        ArrayList<Move> moves = new ArrayList<>(256);

        ArrayList<Integer> queens = queensBuffer();
        ArrayList<Integer> destinations = destinationsBuffer();
        ArrayList<Integer> arrowTargets = arrowTargetsBuffer();

        collectQueenPositions(board, sideToMove, queens);
        for (int queenPos : queens) {
            getReachableSquaresInto(board, queenPos, destinations);
            for (int destination : destinations) {
                getArrowTargetsAfterMoveInto(board, queenPos, destination, sideToMove, arrowTargets);
                for (int arrow : arrowTargets) {
                    moves.add(new Move(queenPos, destination, arrow));
                }
            }
        }

        return moves;
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
