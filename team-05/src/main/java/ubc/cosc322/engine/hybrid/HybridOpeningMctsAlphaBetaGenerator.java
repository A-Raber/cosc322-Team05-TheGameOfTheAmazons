package ubc.cosc322.engine.hybrid;

import java.util.ArrayList;

import ubc.cosc322.engine.AbstractMoveGenerator;
import ubc.cosc322.engine.MoveGenerator;
import ubc.cosc322.engine.alphabeta.AlphaBetaMoveGenerator;
import ubc.cosc322.engine.mcts.v2.MCTSv2;
import ubc.cosc322.model.GameState;

/**
 * Hybrid strategy:
 * - Opening: MCTSv2 for broad strategic exploration
 * - Mid/Late game: AlphaBeta for deeper tactical conversion
 */
public class HybridOpeningMctsAlphaBetaGenerator extends AbstractMoveGenerator {

    private static final int DEFAULT_SWITCH_PLY = 18;

    private final MoveGenerator openingGenerator;
    private final MoveGenerator tacticalGenerator;
    private final int switchPly;

    public HybridOpeningMctsAlphaBetaGenerator() {
        this(new MCTSv2(), new AlphaBetaMoveGenerator(), DEFAULT_SWITCH_PLY);
    }

    public HybridOpeningMctsAlphaBetaGenerator(int switchPly) {
        this(new MCTSv2(), new AlphaBetaMoveGenerator(), switchPly);
    }

    public HybridOpeningMctsAlphaBetaGenerator(MoveGenerator openingGenerator, MoveGenerator tacticalGenerator, int switchPly) {
        this.openingGenerator = openingGenerator;
        this.tacticalGenerator = tacticalGenerator;
        this.switchPly = Math.max(1, switchPly);
    }

    @Override
    public ArrayList<Integer>[] generateMove(GameState gameState) {
        int ply = countArrows(gameState.getBoardRef());
        MoveGenerator active = ply < switchPly ? openingGenerator : tacticalGenerator;
        MoveGenerator fallback = ply < switchPly ? tacticalGenerator : openingGenerator;

        ArrayList<Integer>[] move = active.generateMove(gameState);
        if (move != null) {
            return move;
        }
        return fallback.generateMove(gameState);
    }

    private static int countArrows(int[] board) {
        int arrows = 0;
        for (int cell : board) {
            if (cell == GameState.ARROW) {
                arrows++;
            }
        }
        return arrows;
    }
}
