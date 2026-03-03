package ubc.cosc322;

import java.util.ArrayList;

// A simple determinstic greedy move generator.
// Mostly for the purpose of comparison with other methods

// note: genuinely don't ever use this in the actual competition
// since this will literally always lose to Random

// It selects the move that will cause a queen to have the
// most amount of empty spaces to shoot at after moving. Ties
// are handled by selecting the first one encountered.
public class GreedyMoveGenerator extends AbstractMoveGenerator {

    @Override
    @SuppressWarnings("unchecked")
    public ArrayList<Integer>[] generateMove(GameState gameState) {
        int[] board = gameState.getBoardRef();
        int sideToMove = gameState.getSideToMove();

        ArrayList<Integer> queens = queensBuffer();
        ArrayList<Integer> destinations = destinationsBuffer();
        ArrayList<Integer> arrowTargets = arrowTargetsBuffer();

        int bestQueen = -1;
        int bestDest = -1;
        int bestArrow = -1;
        int bestScore = -1;

        collectQueenPositions(board, sideToMove, queens);

        for (int queenPos : queens) {
            getReachableSquaresInto(board, queenPos, destinations);
            if (destinations.isEmpty()) continue;

            for (int dest : destinations) {
                getArrowTargetsAfterMoveInto(board, queenPos, dest, sideToMove, arrowTargets);
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
