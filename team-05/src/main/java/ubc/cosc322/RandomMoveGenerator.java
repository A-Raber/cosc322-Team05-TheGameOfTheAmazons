package ubc.cosc322;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;



public class RandomMoveGenerator extends AbstractMoveGenerator {
	private final Random random;

	public RandomMoveGenerator() {
		this.random = new Random();
	}

	@Override
	@SuppressWarnings("unchecked")
	public ArrayList<Integer>[] generateMove(GameState gameState) {
		
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
}
