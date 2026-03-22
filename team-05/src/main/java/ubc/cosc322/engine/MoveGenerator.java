package ubc.cosc322.engine;

import java.util.ArrayList;
import ubc.cosc322.model.GameState;
import ubc.cosc322.model.Move;

public interface MoveGenerator {
	ArrayList<Integer>[] generateMove(GameState gameState);

	boolean hasAnyLegalMove(GameState gameState, int side);

	default void onOwnMovePlayed(GameState stateAfterOwnMove, Move ownMove) {
	}

	default void onOpponentMoveObserved(GameState stateBeforeOpponentMove, Move opponentMove) {
	}

	default void shutdown() {
	}
}
