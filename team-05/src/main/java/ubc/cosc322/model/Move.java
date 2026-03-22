package ubc.cosc322.model;

public class Move {
    public final int from;
    public final int to;
    public final int arrow;

    public Move(int from, int to, int arrow){
        this.from = from;
        this.to = to;
        this.arrow = arrow;
    }
}
