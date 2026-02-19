package ubc.cosc322;

import java.util.ArrayList;

public class AmazonBoard {
	
	public static final int EMPTY = 0;
	public static final int WHITE = 1;
	public static final int BLACK = 2;
	public static final int ARROW = 3;
	
	public int[][] grid = new int[10][10];
	
	
	
	public void loadFromServer(ArrayList<Integer> state) {
		
		
		for(int i = 0; i < 121; i++) {
			int row = i / 11;
			int col = i % 11;
			if(row == 0 || col == 0)
				continue;
			grid[row - 1][col - 1] = state.get(i);
		}
		
	}
	
	public void applyMove(Move m, int player) {
		grid[m.fr][m.fc] = EMPTY;
	    grid[m.tr][m.tc] = player;
	    grid[m.ar][m.ac] = ARROW;
	}

	public void undoMove(Move m, int player) {
	    grid[m.ar][m.ac] = EMPTY;
	    grid[m.tr][m.tc] = EMPTY;
	    grid[m.fr][m.fc] = player;
	}
	
	public boolean inBounds(int r, int c) {
        return r>=0 && r<10 && c>=0 && c<10;
    }
	
	
	/* Quick display of what the board looks like. Only shows numbers*/
	@Override
	public String toString() {
		
		StringBuilder board = new StringBuilder();
		
		for(int row = 0;row < 10; row++) {
			for(int col = 0; col < 10; col++) {
				
				board.append(grid[row][col] + "  ");
				
			}
			
			board.append("\n");
		}
		
		
		return board.toString();
	}

}
