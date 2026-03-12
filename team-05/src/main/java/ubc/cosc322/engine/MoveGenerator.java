package ubc.cosc322.engine;

import java.util.ArrayList;
import ubc.cosc322.model.GameState;

public interface MoveGenerator {
	ArrayList<Integer>[] generateMove(GameState gameState);

	boolean hasAnyLegalMove(GameState gameState, int side);
}
