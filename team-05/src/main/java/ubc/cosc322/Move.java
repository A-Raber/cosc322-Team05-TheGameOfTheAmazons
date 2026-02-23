package ubc.cosc322;

public class Move {
	
//	public int fr, fc;
//	public int tr, tc;
//	public int ar, ac;
//	
//	public Move() {};
//	
//	public Move(int fr, int fc, int tr, int tc, int ar, int ac) {
//		this.fr = fr;
//		this.fc = fc;
//		this.tr = tr;
//		this.tc = tc;
//		this.ar = ar;
//		this.ac = ac;
//	}
	
	public final int from;
	public final int to;
	public final int arrow;
	
	public Move(int from, int to, int arrow) {
		this.from = from;
		this.to = to;
		this.arrow = arrow;
	}

}
