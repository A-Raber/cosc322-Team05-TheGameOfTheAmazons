package ubc.cosc322;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

public class TextConverter {
	

	  public static Map<String, Object> convertTo(Move move) {
	        HashMap<String, Object> finalMove = new HashMap<>();

	        finalMove.put(AmazonsGameMessage.QUEEN_POS_CURR, convertPosition(move.fr, move.fc));
	        finalMove.put(AmazonsGameMessage.QUEEN_POS_NEXT, convertPosition(move.tr, move.tc));
	        finalMove.put(AmazonsGameMessage.ARROW_POS, convertPosition(move.ar, move.ac));

	        return finalMove;
	    }

	    private static ArrayList<Integer> convertPosition(int row, int col) {
	        ArrayList<Integer> pos = new ArrayList<>(2);

	        // Convert from 0-based (engine) to 1-based (server)
	        pos.add(row + 1);
	        pos.add(col + 1);

	        return pos;
	    }
}
