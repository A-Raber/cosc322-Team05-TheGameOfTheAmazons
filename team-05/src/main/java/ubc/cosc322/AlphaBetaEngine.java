package ubc.cosc322;

import java.util.List;

public class AlphaBetaEngine {

    private Timer timer = new Timer();

    public Move searchBestMove(AmazonBoard board, int player) {
        timer.start();
        Move best = null;

        for (int depth = 1; depth <= 10; depth++) {
            if (timer.timeUp()) break;
            best = alphaBetaRoot(board, depth, player);
        }
        return best;
    }

    private Move alphaBetaRoot(AmazonBoard b, int depth, int player) {
        List<Move> moves = MoveGenerator.generateMoves(b, player);
        Move bestMove = null;
        int bestScore = Integer.MIN_VALUE;

        for (Move m : moves) {
            b.applyMove(m, player);
            int score = alphaBeta(b, depth-1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            b.undoMove(m, player);

            if (score > bestScore) {
                bestScore = score;
                bestMove = m;
            }
        }
        return bestMove;
    }

    private int alphaBeta(AmazonBoard b, int depth, int alpha, int beta, boolean maximizing) {
        if (depth == 0 || timer.timeUp()) {
            return Evaluator.evaluate(b);
        }

        int player = maximizing ? AmazonBoard.WHITE : AmazonBoard.BLACK;
        List<Move> moves = MoveGenerator.generateMoves(b, player);

        if (moves.isEmpty()) {
            return maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }

        if (maximizing) {
            int value = Integer.MIN_VALUE;
            for (Move m : moves) {
                b.applyMove(m, player);
                value = Math.max(value, alphaBeta(b, depth-1, alpha, beta, false));
                b.undoMove(m, player);
                alpha = Math.max(alpha, value);
                if (alpha >= beta) break;
            }
            return value;
        } else {
            int value = Integer.MAX_VALUE;
            for (Move m : moves) {
                b.applyMove(m, player);
                value = Math.min(value, alphaBeta(b, depth-1, alpha, beta, true));
                b.undoMove(m, player);
                beta = Math.min(beta, value);
                if (beta <= alpha) break;
            }
            return value;
        }
    }
}
