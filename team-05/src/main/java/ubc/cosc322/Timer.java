package ubc.cosc322;

public class Timer {
	
	private long start;
	private long max_time = 27000;
	
	public void start() {
		start = System.currentTimeMillis();
	}
	
	public boolean timeUp() {
		
		boolean overtime = System.currentTimeMillis() - start > max_time; 
		
		if (overtime) {
			System.out.println("reached team 5 internal timer threshold");
			return true;
		}
		
		return false;
	}
	
}
