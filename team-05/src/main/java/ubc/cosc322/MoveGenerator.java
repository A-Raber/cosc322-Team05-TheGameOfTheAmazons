package ubc.cosc322;

import java.util.ArrayList;
import java.util.List;


public class MoveGenerator {
	
	// 8 directions: N, NE, E, SE, S, SW, W, NW
	/*
	 * Idea: have it so that it first calculates in the direction of the opposite team
	 * Plus have it calculate West to North-East-South so it calculates the front of the player first
	 */
	private static final int[] DX = {-1, -1, 0, 1, 1, 1, 0, -1};
	private static final int[] DY = {0, 1, 1, 1, 0, -1, -1, -1};
	
	public static List<Move> generateMoves(GameState state){
		List<Move> moves = new ArrayList<>();
		int side = state.getSideToMove();
		int[] board = state.copyBoard1D();
		
		for(int idx = 0; idx < GameState.BOARD_CELLS; idx++) {
			if(board[idx] == side) {
				moves.addAll(generateQueenMoves(state, idx));
			}
		}
		return moves;
	}
	
	private static List<Move> generateQueenMoves(GameState state, int fromIndex){
		List<Move> moves = new ArrayList<>();
		int boardSize = GameState.BOARD_SIZE;
		int[] board = state.copyBoard1D();
		
		int row = fromIndex / boardSize;
		int col = fromIndex % boardSize;
		
		for(int dir = 0; dir < 8; dir++) {
			int r = row + DX[dir];
			int c = col + DY[dir];
			
			while(isInBounds(r, c) && board[r * boardSize + c] == GameState.EMPTY) {
				int toIndex = r * boardSize + c;
				
				// Temp move queen to position 
				board[fromIndex] = GameState.EMPTY;
				board[toIndex] = state.getSideToMove();
				
				// Generate all arrows shots from new position
				moves.addAll(generateArrowShots(board, toIndex, fromIndex));
				
				// Undo temp move for queen
				board[fromIndex] = state.getSideToMove();
				board[toIndex] = GameState.EMPTY;
				
				
				// Moves in current direction until it is blocked by wall/arrow/player
				r += DX[dir];
				c += DY[dir];
			}
		}
		return moves;
	}
	
	private static List<Move> generateArrowShots(int[] board, int toIndex, int fromIndex){
		List<Move> moves = new ArrayList<>();
		int boardSize = GameState.BOARD_SIZE;
		int row = toIndex / boardSize;
		int col = fromIndex % boardSize;
		
		for(int dir = 0; dir < 8; dir++) {
			int r = row + DX[dir];
			int c = col + DY[dir];
			
			while(isInBounds(r, c) && board[r * boardSize + c] == GameState.EMPTY) {
				int arrowIndex = r * boardSize + c;
				moves.add(new Move(fromIndex, toIndex, arrowIndex));
				r += DX[dir];
				c += DY[dir];
			}
		}
		return moves;
	}
	
	
	private static boolean isInBounds(int row, int col) {
		return row >= 0 && row < GameState.BOARD_SIZE && col >= 0 && col < GameState.BOARD_SIZE;
	}
}
