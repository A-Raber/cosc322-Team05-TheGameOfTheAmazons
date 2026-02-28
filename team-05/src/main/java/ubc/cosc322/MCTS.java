package ubc.cosc322;

import java.util.*;

// Helper methods and direction constants are inherited from AbstractMoveGenerator
public class MCTS extends AbstractMoveGenerator {
    private static final double EXPLORATION_CONSTANT = Math.sqrt(2);
    private final Random random = new Random();

    // Change DEFAULT_MAX_NODES to match your available memory.
    // From my testing, every GB of ram is good for around 1 million extra nodes
    // It starts to play really well past the 8 million mark
    // Keep in mind that every 100K nodes = 34-39 ms per call
    private static final int DEFAULT_MAX_NODES = 15_000_000;
    private final int maxNodes;

    private int nodeCount;
    // one-time warning flag when node cap is hit
    private boolean warnedNodeCap = false;

    public MCTS() {
        this(DEFAULT_MAX_NODES);
    }

    public MCTS(int maxNodes) {
        this.maxNodes = Math.max(1, maxNodes);
    }

    @Override
    public ArrayList<Integer>[] generateMove(GameState gameState) {
        Timer timer = new Timer();
        timer.start();
        MCTSNode root = new MCTSNode(gameState.copy());
        // System.out.println("Starting MCTS! Side to move: " + gameState.getSideToMove());
        
        this.nodeCount = 1;
        final int rootPlayer = gameState.getSideToMove();

        int iterations = 0;
        int MAX_ITERATIONS = 20000; // Tune as needed
        while (!timer.timeUp() && iterations < MAX_ITERATIONS && nodeCount < maxNodes) {
            // 1. Selection
            MCTSNode node = selectPromisingNode(root);
            // 2. Expansion
            if (!isTerminal(node.state)) {
                expandNode(node);
            }
            // 3. Simulation
            MCTSNode nodeToSimulate = node;
            if (!node.children.isEmpty()) {
                nodeToSimulate = node.children.get(random.nextInt(node.children.size()));
            }
            int result = simulateRandomPlayout(nodeToSimulate.state.copy(), rootPlayer);
            // 4. Backpropagation
            backPropagate(nodeToSimulate, result);
            iterations++;
        }
        // System.out.println("MCTS iterations: " + iterations);
        // Return the move with the highest visit count
        MCTSNode bestChild = root.children.stream()
                .max(Comparator.comparingInt(n -> n.visitCount))
                .orElse(null);
        if (bestChild == null) return null;
        //System.out.println("Best move chosen: from " + bestChild.move.from + " to " + bestChild.move.to + " arrow " + bestChild.move.arrow);
        // Convert Move to ArrayList<Integer>[]
        return moveToArray(bestChild.move);
    }

    private MCTSNode selectPromisingNode(MCTSNode node) {
        while (!node.children.isEmpty()) {
            node = node.children.stream()
                    .max(Comparator.comparingDouble(this::uctValue))
                    .orElse(node);
        }
        return node;
    }

    private void expandNode(MCTSNode node) {
        List<Move> moves = getAllPossibleMoves(node.state);
        for (Move move : moves) {
            if (nodeCount >= maxNodes) {
                // reached global node cap, stop expanding further
                if (!warnedNodeCap) {
                    System.err.println("Warning: MCTS node cap reached (" + maxNodes + ")");
                    warnedNodeCap = true;
                }
                break;
            }
            GameState nextState = node.state.copy();
            applyMove(nextState, move);
            node.children.add(new MCTSNode(nextState, node, move));
            nodeCount++;
        }
    }

    // Use a max playout depth for heuristic evaluation
    private static final int MAX_PLAYOUT_DEPTH = 30;

    private int simulateRandomPlayout(GameState state, int rootPlayer) {
        int depth = 0;
        while (!isTerminal(state) && depth < MAX_PLAYOUT_DEPTH) {
            List<Move> moves = getAllPossibleMoves(state);
            if (moves.isEmpty()) break;
            Move move = moves.get(random.nextInt(moves.size()));
            applyMove(state, move);
            depth++;
        }
        if (isTerminal(state)) {
            return getResult(state, rootPlayer);
        } else {
            // Use territory control heuristic
            return evaluateTerritory(state, rootPlayer);
        }
    }

    // Heuristic: 1 if root player has much more territory, 0 if much less, 0.5 if close
    private int evaluateTerritory(GameState state, int rootPlayer) {
        int myTerritory = countTerritory(state, rootPlayer);
        int oppSide = (rootPlayer == GameState.BLACK) ? GameState.WHITE : GameState.BLACK;
        int oppTerritory = countTerritory(state, oppSide);
        int diff = myTerritory - oppTerritory;
        // System.out.println("Heuristic eval: myTerritory=" + myTerritory + ", oppTerritory=" + oppTerritory + ", diff=" + diff);
        if (diff > 2) return 1;
        if (diff < -2) return 0;
        return 0;
    }

    // Count number of squares reachable by a side
    private int countTerritory(GameState state, int side) {
        int[] board = state.copyBoard();
        boolean[] visited = new boolean[GameState.BOARD_CELLS];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        // Add all queens of this side
        for (int i = 0; i < GameState.BOARD_CELLS; i++) {
            if (board[i] == side) {
                queue.add(i);
                visited[i] = true;
            }
        }
        int count = 0;
        while (!queue.isEmpty()) {
            int pos = queue.poll();
            count++;
            for (int offset : DIRECTION_OFFSETS) {
                int current = pos;
                while (true) {
                    int next = current + offset;
                    if (!isInsideBoard(next) || crossesRowBoundary(current, next) || board[next] != GameState.EMPTY || visited[next]) {
                        break;
                    }
                    visited[next] = true;
                    queue.add(next);
                    current = next;
                }
            }
        }
        return count;
    }

    private void backPropagate(MCTSNode node, int result) {
        while (node != null) {
            node.visitCount++;
            node.winScore += result;
            node = node.parent;
        }
    }

    private double uctValue(MCTSNode node) {
        if (node.visitCount == 0) return Double.MAX_VALUE;
        return node.winScore / node.visitCount +
                EXPLORATION_CONSTANT * Math.sqrt(Math.log(node.parent.visitCount + 1) / node.visitCount);
    }

  

    // Generate all legal moves for the current player, with debug output
    private List<Move> getAllPossibleMoves(GameState state) {
        int[] board = state.getBoardRef();
        int sideToMove = state.getSideToMove();
        ArrayList<Move> moves = new ArrayList<>();

        ArrayList<Integer> queens = queensBuffer();
        ArrayList<Integer> destinations = destinationsBuffer();
        ArrayList<Integer> arrowTargets = arrowTargetsBuffer();

        collectQueenPositions(board, sideToMove, queens);
        for (int queenPos : queens) {
            getReachableSquaresInto(board, queenPos, destinations);
            for (int dest : destinations) {
                getArrowTargetsAfterMoveInto(board, queenPos, dest, sideToMove, arrowTargets);
                for (int arrow : arrowTargets) {
                    moves.add(new Move(queenPos, dest, arrow));
                }
            }
        }
        //System.out.println("getAllPossibleMoves for side " + sideToMove + ": " + moves.size() + " moves");
        return moves;
    }

    // Check if the current player has any legal moves
    private boolean isTerminal(GameState state) {
        return getAllPossibleMoves(state).isEmpty();
    }

    // Return 1 if the previous player (not the one to move) has won, 0 otherwise, with debug
    private int getResult(GameState state, int rootPlayer) {
        int winner = (state.getSideToMove() == GameState.BLACK) ? GameState.WHITE : GameState.BLACK;
        // System.out.println("Game ended. Winner: " + (winner == GameState.WHITE ? "WHITE" : "BLACK"));
        return (winner == rootPlayer) ? 1 : 0;
    }

    // Apply a move to the state
    // Use the new GameState.applyMove(Move move)
    private void applyMove(GameState state, Move move) {
        state.applyMove(move);
    }

    // Convert Move to ArrayList<Integer>[] for the server
    @SuppressWarnings("unchecked")
    private ArrayList<Integer>[] moveToArray(Move move) {
        return new ArrayList[] {
            toServerPosition(move.from),
            toServerPosition(move.to),
            toServerPosition(move.arrow)
        };
    }

    @Override
    public boolean hasAnyLegalMove(GameState gameState, int side) {
        // TODO: Implement this if needed
        return false;
    }
}
