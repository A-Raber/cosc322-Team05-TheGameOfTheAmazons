package ubc.cosc322;

import java.util.ArrayList;
import java.util.List;


public class MoveGenerator {
	
    private static final int[] dr = {-1,-1,-1,0,0,1,1,1};
    private static final int[] dc = {-1,0,1,-1,1,-1,0,1};
    
    public static List<Move> generateMoves(AmazonBoard b, int player){
    	List<Move> moves = new ArrayList<>();
    	
    	for (int r=0;r<10;r++) {
            for (int c=0;c<10;c++) {
                if (b.grid[r][c] != player) continue;

                for (int d=0; d<8; d++) {
                    int nr=r+dr[d], nc=c+dc[d];
                    while (b.inBounds(nr,nc) && b.grid[nr][nc]==AmazonBoard.EMPTY) {

                        // move queen temporarily
                        b.grid[r][c] = AmazonBoard.EMPTY;
                        b.grid[nr][nc] = player;

                        generateArrows(b, r, c, nr, nc, moves);

                        // undo temp move
                        b.grid[r][c] = player;
                        b.grid[nr][nc] = AmazonBoard.EMPTY;

                        nr+=dr[d]; nc+=dc[d];
                    }
                }
            }
        }
    	
    	return moves;
    }
    private static void generateArrows(AmazonBoard b, int fr,int fc,int tr,int tc,List<Move> moves){
        for(int d=0; d<8; d++){
            int ar=tr+dr[d], ac=tc+dc[d];
            while(b.inBounds(ar,ac) && b.grid[ar][ac]==AmazonBoard.EMPTY){
                moves.add(new Move(fr,fc,tr,tc,ar,ac));
                ar+=dr[d]; ac+=dc[d];
            }
        }
    }
}
