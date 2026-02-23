package ubc.cosc322;

import java.util.ArrayList;
import java.util.Random;

public interface MoveGenerator {
	ArrayList<Integer>[] generateMove(GameState gameState, Random random);
}
