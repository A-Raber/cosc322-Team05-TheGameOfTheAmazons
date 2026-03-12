package ubc.cosc322.util;

/*
    Utility timer used to enfoce a time limit during seach algorithms

    It is set to trigger when 27 seconds has passed instead of 30 to 
    allocate time to ensure latency with the server will not result 
    in a timeout.

    Typical usage:
    Timer timer = new Timer();
    timer.start();
    while(!timer.timeUp()){
        // perform searching
    }
*/

public class Timer {
    private long start;

    public void start(){
        start = System.currentTimeMillis();
    }
    public boolean timeUp(){
        return (System.currentTimeMillis() - start) > 27000;
    }
}
