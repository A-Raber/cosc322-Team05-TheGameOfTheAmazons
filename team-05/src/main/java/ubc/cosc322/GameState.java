package ubc.cosc322;

import java.util.List;

public class GameState {
    
	public static final int BOARD_SIZE = 10;
	public static final int BOARD_CELLS = BOARD_SIZE * BOARD_SIZE;
	public static final int EMPTY = 0;
	public static final int BLACK = 1;
	public static final int WHITE = 2;
	public static final int ARROW = 3;

	private final int[] board;
	private int sideToMove;
	private String blackPlayer;
	private String whitePlayer;

	public GameState() {
		this.board = new int[BOARD_CELLS];
		this.sideToMove = BLACK;
	}

	public void loadFromServerBoard(List<Integer> serverBoard) {
		int writeIndex = 0;
		for (int row = 1; row <= BOARD_SIZE; row++) {
			int base = row * 11;
			for (int col = 1; col <= BOARD_SIZE; col++) {
				board[writeIndex++] = serverBoard.get(base + col);
			}
		}

	}

	public void applyMove(List<Integer> queenPositionCurrent, List<Integer> queenPositionNext, List<Integer> arrowPosition) {
		int from = XYtoBoardPosition(queenPositionCurrent);
		int to = XYtoBoardPosition(queenPositionNext);
		int arrow = XYtoBoardPosition(arrowPosition);

		int movingPiece = board[from];
		board[from] = EMPTY;
		board[to] = movingPiece;
		board[arrow] = ARROW;
		toggleSideToMove();
	}

	public void undoMove(int from, int to, int arrow, int movingPiece) {
		board[arrow] = EMPTY;
		board[to] = EMPTY;
		board[from] = movingPiece;
		toggleSideToMove();
	}

	public int getSideToMove() {
		return sideToMove;
	}

	public void setSideToMove(int sideToMove) {
		this.sideToMove = sideToMove;
	}

	public int[] copyBoard() {
		return board.clone();
	}

	public int getCell(int row, int col) {
		return board[row * BOARD_SIZE + col];
	}

	public void setBlackPlayer(String blackPlayer) {
		this.blackPlayer = blackPlayer;
	}

	public void setWhitePlayer(String whitePlayer) {
		this.whitePlayer = whitePlayer;
	}

	public String getBlackPlayer() {
		return blackPlayer;
	}

	public String getWhitePlayer() {
		return whitePlayer;
	}

	private void toggleSideToMove() {
		sideToMove = (sideToMove == BLACK) ? WHITE : BLACK;
	}

	// utility methods for loading test states for benchmarking
	public void setCell(int row, int col, int value) {
		board[row * BOARD_SIZE + col] = value;
	}
	public void setCell(int idx, int value) {
		board[idx] = value;
	}
	public void clearBoard() {
		for (int i = 0; i < BOARD_CELLS; i++) {
			board[i] = EMPTY;
		}
	}

	private int XYtoBoardPosition(List<Integer> position) {
		int row = position.get(0) - 1;
		int col = position.get(1) - 1;
		return row * BOARD_SIZE + col;
	}
}