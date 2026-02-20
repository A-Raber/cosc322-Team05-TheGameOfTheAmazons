package ubc.cosc322;

import java.util.ArrayList;
import java.util.List;

public class AlphaBetaEngine {
	
	private int INF = Integer.MAX_VALUE;
	private int NEG_INF = Integer.MIN_VALUE;
    private Timer timer = new Timer();
    
    private int depth = 6; // try playing with this
    private int self_colour;
    private AmazonBoard board;

    /*
     * @param board
     * @param self_colour: either AmazonBoard.WHITE (1) or AmazonBoard.BLACK (2)
     */
    public AlphaBetaEngine(AmazonBoard board, int self_colour) {
    	this.board = board;
    	this.self_colour = self_colour;
    }
    
    
    public Move searchBestMove() {
        timer.start();
        
        Move best_move = null;
        int best_score = NEG_INF;
        
        int alpha = NEG_INF;
        int beta = INF;
        
        /*
         * consider iterative deepening, i.e. search depth 1, then 2, etc.
         * and break if timer runs out
         * 
         * TODO: handle no possible moves?
         */
        
        for (Move move : MoveGenerator.generateMoves(board, self_colour)) {
        	if (timer.timeUp()) break;
        	board.applyMove(move);
        	
        	// call with depth-1 because we've basically done a move now, so depth is one less
        	// call it for the next player (it's the other players turn for the following move)
        	alpha = Math.max(alpha, miniMax(board, depth -1, alpha, beta, next_player(self_colour)));
        	
        	// undo our move
        	board.undoMove(move);
        	
        	if (alpha > best_score) {
        		best_score = alpha;
        		best_move = move;
        	}
       
        }
        return best_move;
    }
    
    private int miniMax(AmazonBoard board, int depth, int alpha, int beta, int player) {
    	// if we are at the end of our depth, return evaluation of current state
    	if (depth <= 0)
    		return heuristic_eval(board);
    	
    	// if it's our turn (max)
    	if (player == self_colour) {
    		int currentAlpha = NEG_INF;
    		
    		// check children
    		for (Move move : MoveGenerator.generateMoves(board, player)) {
    			if (timer.timeUp()) break;
    			board.applyMove(move);
    			
    			// set current alpha
    			currentAlpha = Math.max(currentAlpha, miniMax(board, depth-1, alpha, beta, next_player(player)));
    			alpha = Math.max(alpha, currentAlpha);
            
    			// undo our move
    			board.undoMove(move);
            
    			// stop searching this branch (prune) if alpha >= beta
    			if (alpha >= beta)
    				return alpha;
    		}
			return currentAlpha;
    	}
    	
    	// if it's opponents turn (min)
    	int currentBeta = INF;
    	for (Move move : MoveGenerator.generateMoves(board, player)) {
    		if (timer.timeUp()) break;
			board.applyMove(move);
			
			// set current beta
			currentBeta = Math.min(currentBeta, miniMax(board, depth-1, alpha, beta, next_player(player)));
			beta = Math.min(beta, currentBeta);
			
			// undo our move
			board.undoMove(move);
			
			// stop searching this branch (prune) if beta <= alpha
			if (beta <= alpha)
				return beta;
    	}
    	return currentBeta;
    	
    	
    	/*
    	 * TODO: handle no possible moves?
    	 */
    	    	
    }
    
    
	/*
	 * heuristic function: number of queen moves we can make 
	 * minus number of queen moves opponent can make (excludes arrow shots).
	 * Should be a somewhat not horrible for determining
	 * how trapped we are vs. opponent.
	 * 
	 * Try playing around with this function.
	 * 
	 * Different one for endgame?? Like maybe how much territory is controlled
	 */
    private int heuristic_eval(AmazonBoard board) {
    	

    	int opp_colour = self_colour == AmazonBoard.WHITE ? AmazonBoard.BLACK : AmazonBoard.WHITE;
    	
    	int self_moves = MoveGenerator.generateQueenMoves(board, self_colour);
    	int opp_moves = MoveGenerator.generateQueenMoves(board, opp_colour);
    	
	    return self_moves - opp_moves;
    	
    }
    
    private int next_player(int current_player) {
    	return current_player == AmazonBoard.WHITE ? AmazonBoard.BLACK : AmazonBoard.WHITE;
    }
    
}
