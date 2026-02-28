package ubc.cosc322;

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
            new GreedyMoveGenerator()
        };

        GameState state = createSampleState();

        for (MoveGenerator gen : generators) {
            long start = System.nanoTime();
            for (int i = 0; i < trials; i++) {
                gen.generateMove(state);
            }
            long elapsed = System.nanoTime() - start;

                System.out.printf("%s: total %d ns, average %.2f ns per call\n",
                    gen.getClass().getSimpleName(), elapsed, (double) elapsed / trials);
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