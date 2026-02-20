package ubc.cosc322;

public class Move {
	
	public int fr, fc;  // queen "from" row, column
	public int tr, tc;  // queen "to" row, column
	public int ar, ac;	// arrow row, column
	public int player; // the player who plays this move
	
	public Move() {};
	
	public Move(int fr, int fc, int tr, int tc, int ar, int ac, int player) {
		this.fr = fr;
		this.fc = fc;
		this.tr = tr;
		this.tc = tc;
		this.ar = ar;
		this.ac = ac;
		this.player = player;
	}

}
