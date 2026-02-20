
package ubc.cosc322;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import sfs2x.client.entities.Room;
import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.GameClient;
import ygraph.ai.smartfox.games.GameMessage;
import ygraph.ai.smartfox.games.GamePlayer;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;
import ubc.cosc322.AmazonBoard;
import ubc.cosc322.TextConverter;


/**
 * An example illustrating how to implement a GamePlayer
 * 
 * @author Yong Gao (yong.gao@ubc.ca) Jan 5, 2021
 *
 */
public class HumanPlayer extends GamePlayer {

	private GameClient gameClient = null;
	private BaseGameGUI gamegui = null;

	private String userName = "C2";
	private String passwd = null;
	AmazonBoard board = new AmazonBoard();
	AlphaBetaEngine engine = new AlphaBetaEngine();

	/**
	 * The main method
	 * 
	 * @param args for name and passwd (current, any string would work)
	 */
	public static void main(String[] args) {
		HumanPlayer player = new HumanPlayer(args[0], args[1]);

		if (player.getGameGUI() == null) {
			player.Go();
		} else {
			BaseGameGUI.sys_setup();
			java.awt.EventQueue.invokeLater(new Runnable() {
				public void run() {
					player.Go();
				}
			});
		}
	}

	/**
	 * Any name and passwd
	 * 
	 * @param userName
	 * @param passwd
	 */
	public HumanPlayer(String userName, String passwd) {
		this.userName = userName;
		this.passwd = passwd;

		// To make a GUI-based player, create an instance of BaseGameGUI
		// and implement the method getGameGUI() accordingly
		this.gamegui = new BaseGameGUI(this);
	}

	@Override
	public void onLogin() {
		userName = gameClient.getUserName();
		if (gamegui != null) {
			gamegui.setRoomInformation(gameClient.getRoomList());
		}
	}

	@Override
	public boolean handleGameMessage(String messageType, Map<String, Object> msgDetails) {
		// This method will be called by the GameClient when it receives a game-related
		// message
		// from the server.

		// For a detailed description of the message types and format,
		// see the method GamePlayer.handleGameMessage() in the game-client-api
		// document.
		
		System.out.println("Received game message - Type:" + messageType + ", Details: " + msgDetails.get(AmazonsGameMessage.GAME_STATE));
		
		
		switch(messageType) {
		case GameMessage.GAME_STATE_BOARD:
			handleGameState(msgDetails);
			break;
		case GameMessage.GAME_ACTION_START:
			handleStart(msgDetails);
			break;
		case GameMessage.GAME_ACTION_MOVE:
			handleMove(msgDetails);
			break;
		}
	
		
//    	if (messageType.equals("cosc322.game-state.board")) {
//    		gamegui.setGameState((ArrayList<Integer>) msgDetails.get("game-state"));
//    	} else if (messageType.equals("cosc322.game-action.move")) {
//    		gamegui.updateGameState((ArrayList<Integer>) msgDetails.get("queen-position-current"), (ArrayList<Integer>) msgDetails.get("queen-position-next"), (ArrayList<Integer>) msgDetails.get("arrow-position"));
//    	}
//    	
   
//    	board.loadFromServer((ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE));
//    	
//    	System.out.println(board.toString());
    	
		return true;
		
	}
	
	
	public void handleGameState(Map<String, Object> msgDetails) {
		if(gamegui != null) 
			gamegui.setGameState((ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE));
		
		board.loadFromServer((ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE));
		
		
		
	}
	
	public void handleStart(Map<String, Object> msgDetails) {
		
		/* TODO: Need to detect which player I am
		 * Also need to convert the best move into what the server can read return: Map<String, Object> 
		 */
		
		Move best = engine.searchBestMove(board, AmazonBoard.WHITE);
		//gameClient.sendMoveMessage(TextConverter.convertTo(best));
		
		System.out.println("Handle Start");
		
	}
	
	public void handleMove(Map<String, Object> msgDetails) {
		
		System.out.println("Handle Move");
	}
	

	@Override
	public String userName() {
		return userName;
	}

	@Override
	public GameClient getGameClient() {
		// TODO Auto-generated method stub
		return this.gameClient;
	}

	@Override
	public BaseGameGUI getGameGUI() {
		// TODO Auto-generated method stub
		return this.gamegui;
	}

	@Override
	public void connect() {
		// TODO Auto-generated method stub
		gameClient = new GameClient(userName, passwd, this);
	}

}// end of class
