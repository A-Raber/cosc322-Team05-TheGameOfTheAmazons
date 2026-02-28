package ubc.cosc322;

import java.util.ArrayList;
import java.util.Random;

// A simple determinstic greedy move generator.
// Mostly for the purpose of comparison with other methods

// It selects the move that will cause a queen to have the
// most amount of mobility (empty squares) after moving. Ties
// are handled by selecting the first one encountered.
public class GreedyMoveGenerator extends AbstractMoveGenerator {

    @Override
    @SuppressWarnings("unchecked")
    public ArrayList<Integer>[] generateMove(GameState gameState) {
        int[] board = gameState.copyBoard();
        int sideToMove = gameState.getSideToMove();

        ArrayList<Integer> queens = collectQueenPositions(board, sideToMove);

        int bestQueen = -1;
        int bestDest = -1;
        int bestArrow = -1;
        int bestScore = -1;

        for (int queenPos : queens) {
            ArrayList<Integer> destinations = getReachableSquares(board, queenPos);
            if (destinations.isEmpty()) continue;

            for (int dest : destinations) {
                ArrayList<Integer> arrowTargets = getArrowTargetsAfterMove(board, queenPos, dest, sideToMove);
                if (arrowTargets.isEmpty()) continue;

                int score = arrowTargets.size();
                if (score > bestScore) {
                    bestScore = score;
                    bestQueen = queenPos;
                    bestDest = dest;
                    bestArrow = arrowTargets.get(0);
                }
            }
        }

        if (bestScore < 0) {
            return null;
        }

        return new ArrayList[] {
            toServerPosition(bestQueen),
            toServerPosition(bestDest),
            toServerPosition(bestArrow)
        };
    }
}
