package ubc.cosc322;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class RandomMoveGenerator implements MoveGenerator {

	private static final int NORTH = -GameState.BOARD_SIZE;
	private static final int SOUTH = GameState.BOARD_SIZE;
	private static final int WEST = -1;
	private static final int EAST = 1;
	private static final int NORTH_WEST = -GameState.BOARD_SIZE - 1;
	private static final int NORTH_EAST = -GameState.BOARD_SIZE + 1;
	private static final int SOUTH_WEST = GameState.BOARD_SIZE - 1;
	private static final int SOUTH_EAST = GameState.BOARD_SIZE + 1;
	private static final int[] DIRECTION_OFFSETS = {
		NORTH, SOUTH, WEST, EAST, NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST
	};

	@Override
	@SuppressWarnings("unchecked")
	public ArrayList<Integer>[] generateMove(GameState gameState, Random random) {
		int[] board = gameState.copyBoard();
		int sideToMove = gameState.getSideToMove();

		ArrayList<Integer> queens = collectQueenPositions(board, sideToMove);
		Collections.shuffle(queens, random);

		for (int queenPos : queens) {
			ArrayList<Integer> destinations = getReachableSquares(board, queenPos);
			if (destinations.isEmpty()) {
				continue;
			}

			int destination = destinations.get(random.nextInt(destinations.size()));
			ArrayList<Integer> arrowTargets = getArrowTargetsAfterMove(board, queenPos, destination, sideToMove);

			if (arrowTargets.isEmpty()) {
				continue;
			}

			int arrow = arrowTargets.get(random.nextInt(arrowTargets.size()));
			return new ArrayList[] {
				toServerPosition(queenPos),
				toServerPosition(destination),
				toServerPosition(arrow)
			};
		}

		return null;
	}

	private ArrayList<Integer> collectQueenPositions(int[] board, int side) {
		ArrayList<Integer> queens = new ArrayList<>();
		for (int i = 0; i < GameState.BOARD_CELLS; i++) {
			if (board[i] == side) {
				queens.add(i);
			}
		}
		return queens;
	}

	private ArrayList<Integer> getArrowTargetsAfterMove(int[] board, int from, int to, int side) {
		int fromValue = board[from];
		int toValue = board[to];

		board[from] = GameState.EMPTY;
		board[to] = side;
		ArrayList<Integer> arrowTargets = getReachableSquares(board, to);
		board[to] = toValue;
		board[from] = fromValue;

		return arrowTargets;
	}

	private ArrayList<Integer> getReachableSquares(int[] board, int pos) {
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

	private static boolean isInsideBoard(int index) {
		return index >= 0 && index < GameState.BOARD_CELLS;
	}

	private static boolean crossesRowBoundary(int from, int to) {
		return Math.abs(columnOf(from) - columnOf(to)) > 1;
	}

	private static int columnOf(int index) {
		return index % GameState.BOARD_SIZE;
	}

	private static ArrayList<Integer> toServerPosition(int flatIndex) {
		ArrayList<Integer> pos = new ArrayList<>(2);
		pos.add(flatIndex / GameState.BOARD_SIZE + 1);
		pos.add(flatIndex % GameState.BOARD_SIZE + 1);
		return pos;
	}
}
