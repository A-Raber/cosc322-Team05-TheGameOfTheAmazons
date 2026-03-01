package ubc.cosc322;

import java.util.ArrayList;
import java.util.Random;

public interface MoveGenerator {
	ArrayList<Integer>[] generateMove(GameState gameState);

	boolean hasAnyLegalMove(GameState gameState, int side);
}

// Base class for move generators (extend this class)

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

	// thread-local reusable buffers
	private final ThreadLocal<ArrayList<Integer>> threadLocalQueens = ThreadLocal.withInitial(() -> new ArrayList<>());
	private final ThreadLocal<ArrayList<Integer>> threadLocalDestinations = ThreadLocal.withInitial(() -> new ArrayList<>());
	private final ThreadLocal<ArrayList<Integer>> threadLocalArrowTargets = ThreadLocal.withInitial(() -> new ArrayList<>());

	// getters for buffers
	protected ArrayList<Integer> queensBuffer() {
		return threadLocalQueens.get();
	}

	protected ArrayList<Integer> destinationsBuffer() {
		return threadLocalDestinations.get();
	}

	protected ArrayList<Integer> arrowTargetsBuffer() {
		return threadLocalArrowTargets.get();
	}

	// fill the provided list with queen positions.
	protected void collectQueenPositions(int[] board, int side, ArrayList<Integer> dest) {
		dest.clear();
		for (int i = 0; i < GameState.BOARD_CELLS; i++) {
			if (board[i] == side) {
				dest.add(i);
			}
		}
	}

	// perform move on board temporarily and write reachable squares into result.
	protected ArrayList<Integer> getArrowTargetsAfterMoveInto(int[] board, int from, int to, int side, ArrayList<Integer> result) {
		// avoid mutating the shared board array, since mutating it breaks thread-safety.

		// compute reachable squares from 'to' treating 'from' as EMPTY
		// and 'to' as occupied by 'side' via conditional checks.
		getReachableSquaresWithMoveInto(board, to, from, to, side, result);
		return result;
	}

	// Compute reachable squares from pos, but treat fromIdx as EMPTY and
	// toIdx as occupied by toSide without mutating the board array.
	protected ArrayList<Integer> getReachableSquaresWithMoveInto(int[] board, int pos, int fromIdx, int toIdx, int toSide, ArrayList<Integer> result) {
		result.clear();

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
				int cellValue = board[next];
				if (next == fromIdx) {
					cellValue = GameState.EMPTY;
				} else if (next == toIdx) {
					cellValue = toSide;
				}
				if (cellValue != GameState.EMPTY) {
					break;
				}

				result.add(next);
				current = next;
			}
		}

		return result;
	}

	protected ArrayList<Integer> getReachableSquares(int[] board, int pos) {
		ArrayList<Integer> result = new ArrayList<>();
		getReachableSquaresInto(board, pos, result);
		return result;
	}

	// fill provided list with reachable squares from pos.
	protected ArrayList<Integer> getReachableSquaresInto(int[] board, int pos, ArrayList<Integer> result) {
		result.clear();

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
		int[] board = gameState.getBoardRef();
		ArrayList<Integer> queens = queensBuffer();
		ArrayList<Integer> destinations = destinationsBuffer();
		ArrayList<Integer> arrowTargets = arrowTargetsBuffer();

		collectQueenPositions(board, side, queens);

		for (int queenPos : queens) {
			getReachableSquaresInto(board, queenPos, destinations);
			for (int destination : destinations) {
				getArrowTargetsAfterMoveInto(board, queenPos, destination, side, arrowTargets);
				if (!arrowTargets.isEmpty()) {
					return true;
				}
			}
		}

		return false;
	}
}
