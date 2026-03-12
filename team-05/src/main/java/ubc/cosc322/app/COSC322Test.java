
package ubc.cosc322.app;

import java.util.ArrayList;
import java.util.Map;

import ubc.cosc322.benchmark.MoveGeneratorBenchmark;
import ubc.cosc322.engine.MoveGenerator;
import ubc.cosc322.engine.alphabeta.AlphaBetaMoveGenerator;
import ubc.cosc322.engine.baseline.GreedyMoveGenerator;
import ubc.cosc322.engine.baseline.HumanMoveGenerator;
import ubc.cosc322.engine.baseline.RandomMoveGenerator;
import ubc.cosc322.engine.hybrid.HybridOpeningMctsAlphaBetaGenerator;
import ubc.cosc322.engine.mcts.v1.MCTS;
import ubc.cosc322.engine.mcts.v2.MCTSv2;
import ubc.cosc322.model.GameState;

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
	private final MoveGenerator moveGenerator;
	private int myColor = GameState.BLACK;

	/**
	 * The main method
	 * 
	 * @param args for name and passwd (current, any string would work)
	 */
	public static void main(String[] args) {
		// Run a benchmark by adding "benchmark" as the first argument, followed by
		// an optional number to define the number of trials (default of 1 million)
		if (args.length >= 1 && "benchmark".equalsIgnoreCase(args[0])) {
			int trials = 100;
			if (args.length >= 2) {
				try {
					trials = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {
					System.err.println("Invalid trial count, using default: " + trials);
				}
			}
			MoveGeneratorBenchmark bench = new MoveGeneratorBenchmark(trials);
			bench.run();
			return;
		}
		

		// default behavior for Player
		// now supports greedy, random, mcts, mctsv2, hybrid, alphabeta, or human (manual)
		if (args.length < 2) {
			System.err.println("Usage: COSC322Test <username> <password> [hybrid|greedy|random|mcts|mctsv2|alphabeta|human]");
			return;
		}

		// optional third argument selects the move generator (default: hybrid)
		String genArg = args.length >= 3 ? args[2] : "hybrid";
		MoveGenerator selectedGen;
		if ("hybrid".equalsIgnoreCase(genArg)) {
			selectedGen = new HybridOpeningMctsAlphaBetaGenerator();
		} else if ("random".equalsIgnoreCase(genArg)) {
			selectedGen = new RandomMoveGenerator();
		} else if ("mcts".equalsIgnoreCase(genArg)) {
			selectedGen = new MCTS();
		} else if ("mctsv2".equalsIgnoreCase(genArg)) {
			selectedGen = new MCTSv2();
		} else if ("alphabeta".equalsIgnoreCase(genArg)) {
			selectedGen = new AlphaBetaMoveGenerator();
		} else if ("human".equalsIgnoreCase(genArg)) {
			selectedGen = new HumanMoveGenerator();
		} else {
			selectedGen = new GreedyMoveGenerator();
		}

		COSC322Test player = new COSC322Test(args[0], args[1], selectedGen);

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
		this(userName, passwd, new GreedyMoveGenerator());
	}

	public COSC322Test(String userName, String passwd, MoveGenerator moveGenerator) {
		this.userName = userName;
		this.passwd = passwd;
		this.moveGenerator = moveGenerator == null ? new GreedyMoveGenerator() : moveGenerator;

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
		
//		System.out.println("handleGameMessage -> type: " + messageType + ", details: " + msgDetails);
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
			ArrayList<Integer> queenNext = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_NEXT); // Queen here can't be capitalized because the stupid API labeled both "QUEEN" actions differently
			ArrayList<Integer> arrowPosition = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.ARROW_POS);
			
			System.out.println("Opponent: queen" + queenCurrent.toString() + " -> " + queenNext.toString() + ", arrow -> " + arrowPosition.toString());
			currentGameState.applyMove(queenCurrent, queenNext, arrowPosition);
			gamegui.updateGameState(queenCurrent, queenNext, arrowPosition);

			if (currentGameState.getSideToMove() == myColor) {
				makeAndSendMove();
			}
		}
		return true;
	}

	private void makeAndSendMove() {
		int sideToMove = currentGameState.getSideToMove();
		if (!moveGenerator.hasAnyLegalMove(currentGameState, sideToMove)) {
			int winningColor = (sideToMove == GameState.BLACK) ? GameState.WHITE : GameState.BLACK;
			String winnerName = (winningColor == GameState.BLACK) ? currentGameState.getBlackPlayer() : currentGameState.getWhitePlayer();
			if (winnerName == null) winnerName = "(unknown)";
			String solverName = moveGenerator.getClass().getSimpleName();
			System.out.println("Winner: " + (winningColor == GameState.BLACK ? "BLACK" : "WHITE") + "(" + winnerName + ") solver: " + solverName);
			return;
		}

		ArrayList<Integer>[] move = moveGenerator.generateMove(currentGameState);
		if (move == null) {
			int winningColor = (currentGameState.getSideToMove() == GameState.BLACK) ? GameState.WHITE : GameState.BLACK;
			printResults(winningColor);
			return;
		}

		System.out.println("Move: queen " + move[0] + " -> " + move[1] + ", arrow -> " + move[2]);
		currentGameState.applyMove(move[0], move[1], move[2]);
		gamegui.updateGameState(move[0], move[1], move[2]);
		gameClient.sendMoveMessage(move[0], move[1], move[2]);

		int opponentSide = currentGameState.getSideToMove();
		if (!moveGenerator.hasAnyLegalMove(currentGameState, opponentSide)) {
			int winningColor = (opponentSide == GameState.BLACK) ? GameState.WHITE : GameState.BLACK;
			printResults(winningColor);
		}
	}
	private void printResults(int winningColor) {
		String winnerName = (winningColor == GameState.BLACK) ? currentGameState.getBlackPlayer() : currentGameState.getWhitePlayer();
		if (winnerName == null) winnerName = "(unknown)";
		String solverName = moveGenerator.getClass().getSimpleName();
		System.out.println("Winner: " + (winningColor == GameState.BLACK ? "BLACK" : "WHITE") + "(" + winnerName + ")");
		System.out.println("Player Side:" + (getMyColor() == GameState.BLACK ? "BLACK" : "WHITE") + " | Solver: " + solverName);
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