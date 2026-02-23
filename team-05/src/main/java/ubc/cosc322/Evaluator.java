package ubc.cosc322;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class Evaluator {
	private static final int INF = 9999;

    private static final int[] dr = {-1,-1,-1,0,0,1,1,1};
    private static final int[] dc = {-1,0,1,-1,1,-1,0,1};
	
//	  public static int evaluate(AmazonBoard b) {
//	        int whiteMob = MoveGenerator.generateMoves(b, AmazonBoard.WHITE).size();
//	        int blackMob = MoveGenerator.generateMoves(b, AmazonBoard.BLACK).size();
//	        return whiteMob - blackMob;
//	  }
    
//    public static int evaluate(AmazonBoard b) {
//        int[][] whiteDist = bfsQueen(b, AmazonBoard.WHITE);
//        int[][] blackDist = bfsQueen(b, AmazonBoard.BLACK);
//
//        int score = 0;
//
//        for (int r=0; r<10; r++) {
//            for (int c=0; c<10; c++) {
//                if (b.grid[r][c] != AmazonBoard.EMPTY) continue;
//
//                if (whiteDist[r][c] < blackDist[r][c]) score += 1;
//                else if (blackDist[r][c] < whiteDist[r][c]) score -= 1;
//            }
//        }
//
//        return score;
//    }
//    
//    private static int[][] bfsQueen(AmazonBoard b, int player) {
//        int[][] dist = new int[10][10];
//
//        for (int r=0; r<10; r++)
//            Arrays.fill(dist[r], INF);
//
//        Queue<int[]> q = new LinkedList<>();
//
//        // Initialize queue with all amazons of that player
//        for (int r=0; r<10; r++) {
//            for (int c=0; c<10; c++) {
//                if (b.grid[r][c] == player) {
//                    dist[r][c] = 0;
//                    q.add(new int[]{r, c});
//                }
//            }
//        }
//
//        // BFS expansion
//        while (!q.isEmpty()) {
//            int[] cur = q.poll();
//            int r = cur[0], c = cur[1];
//
//            for (int d=0; d<8; d++) {
//                int nr = r + dr[d];
//                int nc = c + dc[d];
//
//                // queen-style sliding
//                while (b.inBounds(nr, nc) && b.grid[nr][nc] == AmazonBoard.EMPTY) {
//                    if (dist[nr][nc] > dist[r][c] + 1) {
//                        dist[nr][nc] = dist[r][c] + 1;
//                        q.add(new int[]{nr, nc});
//                    }
//                    nr += dr[d];
//                    nc += dc[d];
//                }
//            }
//        }
//
//        return dist;
//    }
    
    public static int evaluate(GameState state) {
        int myMobility = MoveGenerator.generateMoves(state).size();
        // Swap side
        state.toggleSideToMove();
        int oppMobility = MoveGenerator.generateMoves(state).size();
        state.toggleSideToMove();

        return myMobility - oppMobility;
    }
    
    public static int estimateMobilityGain(GameState state, Move m) {
        int centerR = 4, centerC = 4; // center of 10x10

        int fromR = m.from / GameState.BOARD_SIZE;
        int fromC = m.from % GameState.BOARD_SIZE;

        int toR   = m.to / GameState.BOARD_SIZE;
        int toC   = m.to % GameState.BOARD_SIZE;

        int before = Math.abs(fromR - centerR) + Math.abs(fromC - centerC);
        int after  = Math.abs(toR   - centerR) + Math.abs(toC   - centerC);

        return before - after; // positive if moving closer to center
    }
    
    private static final int[][] DIRECTIONS = {
    	    {1,0},{-1,0},{0,1},{0,-1},
    	    {1,1},{1,-1},{-1,1},{-1,-1}
    	};
    
    public static int estimateOpponentMobilityLoss(GameState state, Move m) {
        int r = m.arrow / GameState.BOARD_SIZE;
        int c = m.arrow % GameState.BOARD_SIZE;

        int blocked = 0;

        for (int[] d : DIRECTIONS) {
            int nr = r + d[0];
            int nc = c + d[1];

            if (nr >= 0 && nr < GameState.BOARD_SIZE &&
                nc >= 0 && nc < GameState.BOARD_SIZE &&
                state.getCell(nr, nc) == GameState.EMPTY) {
                blocked++;
            }
        }
        return 8 - blocked; // fewer empty squares = more blocking power
    }
}
