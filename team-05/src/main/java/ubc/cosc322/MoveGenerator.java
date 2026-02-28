package ubc.cosc322;

import java.util.ArrayList;
import java.util.Random;

public interface MoveGenerator {
	ArrayList<Integer>[] generateMove(GameState gameState);

	boolean hasAnyLegalMove(GameState gameState, int side);
}

// Base class for move generators.

abstract class AbstractMoveGenerator implements MoveGenerator {

	protected static final int NORTH = -GameState.BOARD_SIZE;
	protected static final int SOUTH = GameState.BOARD_SIZE;
	protected static final int WEST = -1;
	protected static final int EAST = 1;
	protected static final int NORTH_WEST = -GameState.BOARD_SIZE - 1;
	protected static final int NORTH_EAST = -GameState.BOARD_SIZE + 1;
	protected static final int SOUTH_WEST = GameState.BOARD_SIZE - 1;
	protected static final int SOUTH_EAST = GameState.BOARD_SIZE + 1;
	protected static final int[] DIRECTION_OFFSETS = {
		NORTH, SOUTH, WEST, EAST, NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST
	};

	protected ArrayList<Integer> collectQueenPositions(int[] board, int side) {
		ArrayList<Integer> queens = new ArrayList<>();
		for (int i = 0; i < GameState.BOARD_CELLS; i++) {
			if (board[i] == side) {
				queens.add(i);
			}
		}
		return queens;
	}

	protected ArrayList<Integer> getArrowTargetsAfterMove(int[] board, int from, int to, int side) {
		int fromValue = board[from];
		int toValue = board[to];

		board[from] = GameState.EMPTY;
		board[to] = side;
		ArrayList<Integer> arrowTargets = getReachableSquares(board, to);
		board[to] = toValue;
		board[from] = fromValue;

		return arrowTargets;
	}

	protected ArrayList<Integer> getReachableSquares(int[] board, int pos) {
		ArrayList<Integer> result = new ArrayList<>();

		for (int offset : DIRECTION_OFFSETS) {
			int current = pos;
			while (true) {
				int next = current + offset;
				if (!isInsideBoard(next)) {
					break;
				}
				if (crossesRowBoundary(current, next)) {
					break;
				}
				if (board[next] != GameState.EMPTY) {
					break;
				}

				result.add(next);
				current = next;
			}
		}

		return result;
	}

	protected static boolean isInsideBoard(int index) {
		return index >= 0 && index < GameState.BOARD_CELLS;
	}

	protected static boolean crossesRowBoundary(int from, int to) {
		return Math.abs(columnOf(from) - columnOf(to)) > 1;
	}

	protected static int columnOf(int index) {
		return index % GameState.BOARD_SIZE;
	}

	protected static ArrayList<Integer> toServerPosition(int flatIndex) {
		ArrayList<Integer> pos = new ArrayList<>(2);
		pos.add(flatIndex / GameState.BOARD_SIZE + 1);
		pos.add(flatIndex % GameState.BOARD_SIZE + 1);
		return pos;
	}

	// Utility to test whether a side has any legal move.
	public boolean hasAnyLegalMove(GameState gameState, int side) {
		int[] board = gameState.copyBoard();
		ArrayList<Integer> queens = collectQueenPositions(board, side);

		for (int queenPos : queens) {
			ArrayList<Integer> destinations = getReachableSquares(board, queenPos);
			for (int destination : destinations) {
				ArrayList<Integer> arrowTargets = getArrowTargetsAfterMove(board, queenPos, destination, side);
				if (!arrowTargets.isEmpty()) {
					return true;
				}
			}
		}

		return false;
	}
}
