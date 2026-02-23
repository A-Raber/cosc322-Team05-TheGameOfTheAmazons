
package ubc.cosc322;

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.GameClient;
import ygraph.ai.smartfox.games.GameMessage;
import ygraph.ai.smartfox.games.GamePlayer;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/**
 * An example illustrating how to implement a GamePlayer
 * 
 * @author Yong Gao (yong.gao@ubc.ca) Jan 5, 2021
 *
 */
public class COSC322Test extends GamePlayer {

	private GameClient gameClient = null;
	private BaseGameGUI gamegui = null;

	private String userName = null;
	private String passwd = null;
	private final GameState currentGameState = new GameState();
	private final Random random = new Random();
	private final MoveGenerator moveGenerator = new RandomMoveGenerator();
	private int myColor = GameState.BLACK;

	/**
	 * The main method
	 * 
	 * @param args for name and passwd (current, any string would work)
	 */
	public static void main(String[] args) {
		COSC322Test player = new COSC322Test(args[0], args[1]);

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
	public COSC322Test(String userName, String passwd) {
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
	@SuppressWarnings("unchecked")
	public boolean handleGameMessage(String messageType, Map<String, Object> msgDetails) {
		// This method will be called by the GameClient when it receives a game-related
		// message
		// from the server.

		// For a detailed description of the message types and format,
		// see the method GamePlayer.handleGameMessage() in the game-client-api
		// document.
		
		System.out.println("handleGameMessage -> type: " + messageType + ", details: " + msgDetails);
		if (GameMessage.GAME_STATE_BOARD.equals(messageType)) {
			ArrayList<Integer> serverBoard = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE);
			
			currentGameState.loadFromServerBoard(serverBoard);
			currentGameState.setSideToMove(GameState.BLACK);
			gamegui.setGameState(serverBoard);

		} else if (GameMessage.GAME_ACTION_START.equals(messageType)) {
			updatePlayerAssignments(msgDetails);
			if (myColor == GameState.BLACK) {
				makeAndSendMove();
			}

		} else if (GameMessage.GAME_ACTION_MOVE.equals(messageType)) {
			ArrayList<Integer> queenCurrent = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_CURR);
			ArrayList<Integer> queenNext = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_NEXT);
			ArrayList<Integer> arrowPosition = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.ARROW_POS);
			
			currentGameState.applyMove(queenCurrent, queenNext, arrowPosition);
			gamegui.updateGameState(queenCurrent, queenNext, arrowPosition);

			if (currentGameState.getSideToMove() == myColor) {
				makeAndSendMove();
			}
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	private void makeAndSendMove() {
		ArrayList<Integer>[] move = moveGenerator.generateMove(currentGameState, random);
		if (move == null) {
			int winningColor = (currentGameState.getSideToMove() == GameState.BLACK) ? GameState.WHITE : GameState.BLACK;
			System.out.println("Winner color: " + (winningColor == GameState.BLACK ? "BLACK" : "WHITE"));
			return;
		}

		System.out.println("Random move: queen " + move[0] + " -> " + move[1] + ", arrow -> " + move[2]);
		currentGameState.applyMove(move[0], move[1], move[2]);
		gamegui.updateGameState(move[0], move[1], move[2]);
		gameClient.sendMoveMessage(move[0], move[1], move[2]);
	}

	private void updatePlayerAssignments(Map<String, Object> msgDetails) {
		String blackPlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_BLACK);
		String whitePlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_WHITE);

		if (blackPlayer != null) {
			currentGameState.setBlackPlayer(blackPlayer);
		}
		if (whitePlayer != null) {
			currentGameState.setWhitePlayer(whitePlayer);
		}

		String storedBlack = currentGameState.getBlackPlayer();
		String storedWhite = currentGameState.getWhitePlayer();

		if (userName.equals(storedBlack)) {
			myColor = GameState.BLACK;
		} else if (userName.equals(storedWhite)) {
			myColor = GameState.WHITE;
		}
	}

	public GameState getCurrentGameState() {
		return currentGameState;
	}

	public int getMyColor() {
		return myColor;
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