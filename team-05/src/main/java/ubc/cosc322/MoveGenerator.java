package ubc.cosc322;

import java.util.ArrayList;

public class MoveGenerator {

	// possible queen moves, starting from moving to the right and then circling CCW.
	// all possible moves can be defined by n*row_moves[i] and n*col_moves[i]
	public static int[] row_moves = {1, 1, 0, -1, -1, -1, 0, 1}; // possible queen moves, row direction
	public static int[] col_moves = {0, 1, 1, 1, 0, -1, -1, -1}; // possible queen moves, column direction
	
	public static ArrayList<Move> generateMoves(AmazonBoard board, int player) {
	    	
		ArrayList<Move> moves = new ArrayList<Move>(); // TODO: pre-allocate array capacity?
		
		int dr = 0; // row change direction
		int dc = 0; // col change direction
		int n = 0; // how far we move
		int k = 0; // how far we shoot the arrow
		int new_row = 0; // new queen row
		int new_col = 0; // new queen col
		int arrow_dr = 0; // arrow row direction
		int arrow_dc = 0; // arrow col direction
		int arrow_row = 0;
		int arrow_col = 0;
		
		// TODO: maybe we keep an active list of our player locations,
		//		 so that we don't have to search for them every time.
	    for (int r=0; r<10; r++) // find our player locations
	    	for (int c=0; c<10; c++) {
	    		if (board.grid[r][c] != player)
	    			continue;
	    		
	    		// set the current queen location to empty temporarily (so we can shoot an arrow here)
	    		board.grid[r][c] = AmazonBoard.EMPTY;
	    		
	    		// find all available queen moves
	    		for (int queen_move=0; queen_move<8; queen_move++) { 
    				dr = row_moves[queen_move]; 
    				dc = col_moves[queen_move]; 
    				
    				for (n=1; n<10; n++) {
    					new_row = r+dr*n;
    					new_col = c+dc*n;
    					
    					board.grid[new_row][new_col] = player; // not really needed, but just for fun
    					
    					if (!board.inBounds(new_row, new_col) || board.grid[new_row][new_col] != AmazonBoard.EMPTY)
    						break; // stop checking this move type when we run into something or leave board
    					
    					// find available arrow shots for this queen move
    					for (int arrow_move=0; arrow_move<8; arrow_move++) { 
    						arrow_dr = row_moves[arrow_move];
    						arrow_dc = col_moves[arrow_move];
    						
    						for (k=1; k<10; k++) {
    							arrow_row = new_row+arrow_dr*k;
    							arrow_col = new_col+arrow_dc*k;
    							
    							if (!board.inBounds(arrow_row, arrow_col) || board.grid[arrow_row][arrow_col] != AmazonBoard.EMPTY)
    	    						break; // stop checking this move type when we run into something or leave board
    							
    							// add this move to the list
    							moves.add(new Move(r, c, new_row, new_col, arrow_row, arrow_col, player));
    						}
    					}
    					board.grid[new_row][new_col] = AmazonBoard.EMPTY; // return board to original state
    				}
    			}
	    		
	    		board.grid[r][c] = player; // return board to original state
	    			
	    	}
	    return moves;
	    	
	 }
	 

}
