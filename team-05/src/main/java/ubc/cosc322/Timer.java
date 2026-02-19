package ubc.cosc322;

public class Timer {
	
	private long start;
	
	public void start() {
		start = System.currentTimeMillis();
	}
	
	public boolean timeUp() {
		return System.currentTimeMillis() - start > 27000; // 27 seconds times up
	}
	
}
