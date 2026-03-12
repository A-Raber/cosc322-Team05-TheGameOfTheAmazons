package ubc.cosc322.engine.baseline;

import java.util.ArrayList;
import java.util.Scanner;

import ubc.cosc322.engine.AbstractMoveGenerator;
import ubc.cosc322.model.GameState;

// A class which allows the human running to program to choose their move

public class HumanMoveGenerator extends AbstractMoveGenerator {

    private final Scanner scanner = new Scanner(System.in);

    // get user input for move to make
    @Override
    @SuppressWarnings("unchecked")
    public ArrayList<Integer>[] generateMove(GameState gameState) {

        int[] board = gameState.getBoardRef();
        int sideToMove = gameState.getSideToMove();

        while (true) {
            System.out.println("Please enter a move in the following format: " +
                    "\n[Queen From Location], [Queen To Location], [Arrow Location]" +
                    "\nLocations should be of the format [row (#),col (letter)]." +
                    "\ne.g. [2,e], [2,h], [4,h]");

            try {
                // parse input
                String input = scanner.nextLine();
                input = input.replaceAll("\\[|\\]|\\s", "");
                String[] inputArray = input.split(",");

                // if input length not adequate
                if (inputArray.length != 6) {
                    System.out.println("Invalid format. Should have 3 numbers, 3 letters. Try again.");
                    continue;
                }

                // get usable data from input
                int fromRow = Integer.parseInt(inputArray[0]);
                int fromCol = letterToNumber(inputArray[1]);
                int fromIndex = toFlaxIndex(fromRow, fromCol);

                int toRow = Integer.parseInt(inputArray[2]);
                int toCol = letterToNumber(inputArray[3]);
                int toIndex = toFlaxIndex(toRow, toCol);

                int arrowRow = Integer.parseInt(inputArray[4]);
                int arrowCol = letterToNumber(inputArray[5]);
                int arrowIndex = toFlaxIndex(arrowRow, arrowCol);

                // check if move is valid
                if (!isValidMove(board, fromIndex, toIndex, arrowIndex, sideToMove)) {
                    System.out.println("Illegal move. Try again.");
                    continue;
                }

                // return in server format
                return new ArrayList[] {
                        toServerPosition(fromIndex),
                        toServerPosition(toIndex),
                        toServerPosition(arrowIndex)
                };

            } catch (Exception e) {
                System.out.println("Invalid input. Try again.");
            }
        }
    }

    // confirm user input is valid move
    private boolean isValidMove(int[] board, int from, int to, int arrow, int sideToMove) {

        // must move our own queen
        if (board[from] != sideToMove)
            return false;

        ArrayList<Integer> destinations = destinationsBuffer();
        ArrayList<Integer> arrowTargets = arrowTargetsBuffer();

        // check that "to" location is valid
        getReachableSquaresInto(board, from, destinations);
        if (!destinations.contains(to))
            return false;

        // check arrow position is valid
        getArrowTargetsAfterMoveInto(board, from, to, sideToMove, arrowTargets);
        return arrowTargets.contains(arrow); // this is the final test, so we can simply return the value
    }

    // convert letter column input to number
    private int letterToNumber(String letter) {
        return letter.toLowerCase().charAt(0) - 'a' + 1;
    }

    // convert row, col to flat index
    private int toFlaxIndex(int row, int col) {
        return (row-1) * GameState.BOARD_SIZE + (col-1);
    }
}

