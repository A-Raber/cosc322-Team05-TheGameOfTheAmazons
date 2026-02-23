package ubc.cosc322;

import java.util.List;

public class AlphaBetaEngine {

//    private Timer timer = new Timer();
//
//    public Move searchBestMove(AmazonBoard board, int player) {
//        timer.start();
//        Move best = null;
//
//        for (int depth = 1; depth <= 10; depth++) {
//            if (timer.timeUp()) break;
//            best = alphaBetaRoot(board, depth, player);
//        }
//        return best;
//    }
//
//    private Move alphaBetaRoot(AmazonBoard b, int depth, int player) {
//        List<Move> moves = MoveGenerator.generateMoves(b, player);
//        Move bestMove = null;
//        int bestScore = Integer.MIN_VALUE;
//
//        for (Move m : moves) {
//            b.applyMove(m, player);
//            int score = alphaBeta(b, depth-1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
//            b.undoMove(m, player);
//
//            if (score > bestScore) {
//                bestScore = score;
//                bestMove = m;
//            }
//        }
//        return bestMove;
//    }
//
//    private int alphaBeta(AmazonBoard b, int depth, int alpha, int beta, boolean maximizing) {
//        if (depth == 0 || timer.timeUp()) {
//            return Evaluator.evaluate(b);
//        }
//
//        int player = maximizing ? AmazonBoard.WHITE : AmazonBoard.BLACK;
//        List<Move> moves = MoveGenerator.generateMoves(b, player);
//
//        if (moves.isEmpty()) {
//            return maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
//        }
//
//        if (maximizing) {
//            int value = Integer.MIN_VALUE;
//            for (Move m : moves) {
//                b.applyMove(m, player);
//                value = Math.max(value, alphaBeta(b, depth-1, alpha, beta, false));
//                b.undoMove(m, player);
//                alpha = Math.max(alpha, value);
//                if (alpha >= beta) break;
//            }
//            return value;
//        } else {
//            int value = Integer.MAX_VALUE;
//            for (Move m : moves) {
//                b.applyMove(m, player);
//                value = Math.min(value, alphaBeta(b, depth-1, alpha, beta, true));
//                b.undoMove(m, player);
//                beta = Math.min(beta, value);
//                if (beta <= alpha) break;
//            }
//            return value;
//        }
//    }
	

    private static final int INF = 1_000_000;

    public static Move findBestMove(GameState state, int depth, Timer timer) {
        List<Move> moves = MoveGenerator.generateMoves(state);

        // ORDER MOVES (very important!)
        moves.sort((a, b) -> moveScore(state, b) - moveScore(state, a));

        Move bestMove = null;
        int alpha = -INF;
        int beta = INF;

        for (Move m : moves) {
            int movedPiece = state.applyMove(m);
            int score = -alphabeta(state, depth - 1, -beta, -alpha, timer);
            state.undoMove(m, movedPiece);

            if (score > alpha) {
                alpha = score;
                bestMove = m;
            }
        }
        return bestMove;
    }

    private static int alphabeta(GameState state, int depth, int alpha, int beta, Timer timer) {
        if (depth == 0) {
            return Evaluator.evaluate(state);
        }
        
        if (timer.timeUp()) return Evaluator.evaluate(state);

        List<Move> moves = MoveGenerator.generateMoves(state);

        if (moves.isEmpty()) {
            // No moves = loss
            return -100000 + (10 - depth); // depth bonus for faster wins
        }

        // ORDER MOVES for better pruning
        moves.sort((a, b) -> moveScore(state, b) - moveScore(state, a));

        for (Move m : moves) {
            int movedPiece = state.applyMove(m);
            int score = -alphabeta(state, depth - 1, -beta, -alpha, timer);
            state.undoMove(m, movedPiece);

            if (score >= beta) {
                return beta; // prune
            }

            if (score > alpha) {
                alpha = score;
            }
        }

        return alpha;
    }

    // Heuristic ordering function
    private static int moveScore(GameState state, Move m) {
        int score = 0;

        // Prefer central moves (mobility heuristic)
        score += 10 * Evaluator.estimateMobilityGain(state, m);

        // Prefer moves that reduce opponent mobility
        score += 5 * Evaluator.estimateOpponentMobilityLoss(state, m);

//        // Optional: history heuristic bonus (if implemented)
//        score += HistoryHeuristic.getScore(m);

        return score;
    }
	
}
