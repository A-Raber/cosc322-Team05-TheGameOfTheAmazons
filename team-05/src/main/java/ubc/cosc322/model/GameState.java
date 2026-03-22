package ubc.cosc322.model;

import java.util.List;
import java.util.Random;

public class GameState {
    
	public static final int BOARD_SIZE = 10;
	public static final int BOARD_CELLS = BOARD_SIZE * BOARD_SIZE;
	public static final int EMPTY = 0;
	public static final int BLACK = 1;
	public static final int WHITE = 2;
	public static final int ARROW = 3;

	// Zobrist hash table: one random 64-bit value per (cell, pieceType) pair
	private static final long[][] ZOBRIST_PIECE;
	// XOR'd in when it is BLACK's turn, flipped on every toggleSideToMove
	private static final long ZOBRIST_SIDE_BLACK;

	static {
		Random rng = new Random(0x9E3779B97F4A7C15L);
		ZOBRIST_PIECE = new long[BOARD_CELLS][4];
		for (int cell = 0; cell < BOARD_CELLS; cell++) {
			for (int piece = 1; piece <= 3; piece++) {
				ZOBRIST_PIECE[cell][piece] = rng.nextLong();
			}
		}
		ZOBRIST_SIDE_BLACK = rng.nextLong();
	}

	private final int[] board;
	private int sideToMove;
	private String blackPlayer;
	private String whitePlayer;
	private long zobristHash;

	public GameState() {
		this.board = new int[BOARD_CELLS];
		this.sideToMove = BLACK;
	}
	
	public GameState copy(){
		GameState copy = new GameState();
		System.arraycopy(this.board, 0, copy.board, 0, BOARD_CELLS);
		copy.sideToMove = this.sideToMove;
		copy.zobristHash = this.zobristHash;
		return copy;
	}

	public void loadFromServerBoard(List<Integer> serverBoard) {
		int writeIndex = 0;
		for (int row = 1; row <= BOARD_SIZE; row++) {
			int base = row * 11;
			for (int col = 1; col <= BOARD_SIZE; col++) {
				board[writeIndex++] = serverBoard.get(base + col);
			}
		}
		recomputeHash();
	}

	public void applyMove(List<Integer> queenPositionCurrent, List<Integer> queenPositionNext, List<Integer> arrowPosition) {
		int from = XYtoBoardPosition(queenPositionCurrent);
		int to = XYtoBoardPosition(queenPositionNext);
		int arrow = XYtoBoardPosition(arrowPosition);

		int movingPiece = board[from];
		zobristHash ^= ZOBRIST_PIECE[from][movingPiece];
		zobristHash ^= ZOBRIST_PIECE[to][movingPiece];
		zobristHash ^= ZOBRIST_PIECE[arrow][ARROW];
		zobristHash ^= ZOBRIST_SIDE_BLACK;
		board[from] = EMPTY;
		board[to] = movingPiece;
		board[arrow] = ARROW;
		toggleSideToMove();
	}

	public void applyMove(Move move) {
		int from = move.from;
		int to = move.to;
		int arrow = move.arrow;
		int movingPiece = board[from];
		zobristHash ^= ZOBRIST_PIECE[from][movingPiece];
		zobristHash ^= ZOBRIST_PIECE[to][movingPiece];
		zobristHash ^= ZOBRIST_PIECE[arrow][ARROW];
		zobristHash ^= ZOBRIST_SIDE_BLACK;
		board[from] = EMPTY;
		board[to] = movingPiece;
		board[arrow] = ARROW;
		toggleSideToMove();
	}

	public void undoMove(int from, int to, int arrow, int movingPiece) {
		zobristHash ^= ZOBRIST_PIECE[arrow][ARROW];
		zobristHash ^= ZOBRIST_PIECE[to][movingPiece];
		zobristHash ^= ZOBRIST_PIECE[from][movingPiece];
		zobristHash ^= ZOBRIST_SIDE_BLACK;
		board[arrow] = EMPTY;
		board[to] = EMPTY;
		board[from] = movingPiece;
		toggleSideToMove();
	}

	public int getSideToMove() {
		return sideToMove;
	}

	public void setSideToMove(int newSide) {
		if (newSide != sideToMove) {
			zobristHash ^= ZOBRIST_SIDE_BLACK;
			sideToMove = newSide;
		}
	}

	public int[] getBoard() {
		return this.board;
	}

	public int[] copyBoard() {
		return board.clone();
	}

	// Return internal board array reference for performance-sensitive code that
	// will only temporarily mutate and restore the board. Use with care.
	public int[] getBoardRef() {
		return board;
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
		setCell(row * BOARD_SIZE + col, value);
	}
	public void setCell(int idx, int value) {
		int old = board[idx];
		if (old != EMPTY)  zobristHash ^= ZOBRIST_PIECE[idx][old];
		board[idx] = value;
		if (value != EMPTY) zobristHash ^= ZOBRIST_PIECE[idx][value];
	}
	public void clearBoard() {
		for (int i = 0; i < BOARD_CELLS; i++) {
			board[i] = EMPTY;
		}
		zobristHash = (sideToMove == BLACK) ? ZOBRIST_SIDE_BLACK : 0L;
	}

	private int XYtoBoardPosition(List<Integer> position) {
		int row = position.get(0) - 1;
		int col = position.get(1) - 1;
		return row * BOARD_SIZE + col;
	}

	// Returns the moving piece so the caller can pass it to undoMove
	public int applyMoveForSearch(int from, int to, int arrow) {
		int movingPiece = board[from];
		zobristHash ^= ZOBRIST_PIECE[from][movingPiece];
		zobristHash ^= ZOBRIST_PIECE[to][movingPiece];
		zobristHash ^= ZOBRIST_PIECE[arrow][ARROW];
		zobristHash ^= ZOBRIST_SIDE_BLACK;
		board[from] = EMPTY;
		board[to] = movingPiece;
		board[arrow] = ARROW;
		toggleSideToMove();
		return movingPiece;
	}

	public long getZobristHash() {
		return zobristHash;
	}

	// Must be called after any direct board mutation that bypasses applyMove/applyMoveForSearch
	public void recomputeHash() {
		zobristHash = 0L;
		for (int i = 0; i < BOARD_CELLS; i++) {
			if (board[i] != EMPTY) {
				zobristHash ^= ZOBRIST_PIECE[i][board[i]];
			}
		}
		if (sideToMove == BLACK) {
			zobristHash ^= ZOBRIST_SIDE_BLACK;
		}
	}
}