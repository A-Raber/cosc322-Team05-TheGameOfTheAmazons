package ubc.cosc322.engine.mcts.v1;

import java.util.ArrayList;
import java.util.List;

import ubc.cosc322.model.GameState;
import ubc.cosc322.model.Move;

public class MCTSNode {
    GameState state;
    MCTSNode parent;
    List<MCTSNode> children;
    Move move;
    int visitCount;
    double winScore;

    public MCTSNode(GameState state, MCTSNode parent, Move move){
        this.state = state;
        this.parent = parent;
        this.move = move;
        this.children = new ArrayList<>();
        this.visitCount = 0;
        this.winScore = 0.0;
    }

    public MCTSNode(GameState state){
        this(state, null, null);
    }

}

