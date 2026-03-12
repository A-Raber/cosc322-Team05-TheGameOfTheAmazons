package ubc.cosc322.engine.baseline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import ubc.cosc322.engine.AbstractMoveGenerator;
import ubc.cosc322.model.GameState;



public class RandomMoveGenerator extends AbstractMoveGenerator {
	private final Random random;

	public RandomMoveGenerator() {
		this.random = new Random();
	}

	@Override
	@SuppressWarnings("unchecked")
	public ArrayList<Integer>[] generateMove(GameState gameState) {
		int[] board = gameState.getBoardRef();
		int sideToMove = gameState.getSideToMove();

		ArrayList<Integer> queens = queensBuffer();
		ArrayList<Integer> destinations = destinationsBuffer();
		ArrayList<Integer> arrowTargets = arrowTargetsBuffer();

		collectQueenPositions(board, sideToMove, queens);
		Collections.shuffle(queens, random);

		for (int queenPos : queens) {
			getReachableSquaresInto(board, queenPos, destinations);
			if (destinations.isEmpty()) {
				continue;
			}

			int destination = destinations.get(random.nextInt(destinations.size()));
			getArrowTargetsAfterMoveInto(board, queenPos, destination, sideToMove, arrowTargets);

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

