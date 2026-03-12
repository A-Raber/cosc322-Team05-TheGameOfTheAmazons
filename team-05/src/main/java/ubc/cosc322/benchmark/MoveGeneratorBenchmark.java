package ubc.cosc322.benchmark;

import ubc.cosc322.engine.MoveGenerator;
import ubc.cosc322.engine.alphabeta.AlphaBetaMoveGenerator;
import ubc.cosc322.engine.baseline.GreedyMoveGenerator;
import ubc.cosc322.engine.baseline.RandomMoveGenerator;
import ubc.cosc322.engine.hybrid.HybridOpeningMctsAlphaBetaGenerator;
import ubc.cosc322.engine.mcts.v1.MCTS;
import ubc.cosc322.engine.mcts.v2.MCTSv2;
import ubc.cosc322.model.GameState;

public class MoveGeneratorBenchmark {

    private final int trials;

    public MoveGeneratorBenchmark() {
        this(1_000_000);
    }

    public MoveGeneratorBenchmark(int trials) {
        if (trials <= 0) {
            throw new IllegalArgumentException("trials must be positive");
        }
        this.trials = trials;
    }

    public void run() {
        System.out.println("Move generator benchmark: " + trials + " iterations per generator");

        // ADD ANY MOVE GENERATORS HERE
        MoveGenerator[] generators = new MoveGenerator[] {
            new RandomMoveGenerator(),
            new GreedyMoveGenerator(),
            new MCTS(250_000),
            new MCTSv2(300_000),
            new HybridOpeningMctsAlphaBetaGenerator(),
            new AlphaBetaMoveGenerator()
        };

        GameState state = createSampleState();

        for (MoveGenerator gen : generators) {
            long start = System.nanoTime();
            for (int i = 0; i < trials; i++) {
                gen.generateMove(state);
            }
            long elapsed = System.nanoTime() - start;
            double elapsedMs = elapsed / 1_000_000.0;
            double avgNs = (double) elapsed / trials;
            double avgMs = avgNs / 1_000_000.0;
            System.out.printf("%s: total %d ns (%.2f ms), average %.2f ns (%.4f ms) per call\n",
                gen.getClass().getSimpleName(), elapsed, elapsedMs, avgNs, avgMs);
        }
    }

    private static GameState createSampleState() {
        GameState gs = new GameState();
        gs.clearBoard();
        
        // place the four queens for each side in their usual starting
        // corners; it isn't important exactly where they are as long as a
        // few moves are legal.
        gs.setCell(0, 3, GameState.BLACK);
        gs.setCell(0, 6, GameState.BLACK);
        gs.setCell(3, 0, GameState.BLACK);
        gs.setCell(3, 9, GameState.BLACK);

        gs.setCell(6, 0, GameState.WHITE);
        gs.setCell(6, 9, GameState.WHITE);
        gs.setCell(9, 3, GameState.WHITE);
        gs.setCell(9, 6, GameState.WHITE);

        gs.setSideToMove(GameState.BLACK);
        return gs;
    }
}