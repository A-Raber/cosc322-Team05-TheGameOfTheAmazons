package ubc.cosc322;

public class Search {
	   /**
     * Iterative deepening wrapper.
     * Starts from depth 1 and increases until timer runs out.
     */
    public static Move iterativeDeepening(GameState state, Timer timer) {
        Move bestMove = null;
        int depth = 1;

        while (!timer.timeUp()) {
            // Use your alpha-beta search at current depth
            Move current = AlphaBetaEngine.findBestMove(state, depth, timer);

            // Only keep the result if search finished before timeout
            if (!timer.timeUp() && current != null) {
                bestMove = current;
            }

            depth++;
        }

        return bestMove;
    }

}
