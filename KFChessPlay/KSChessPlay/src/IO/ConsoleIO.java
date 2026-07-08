package IO;

import Controller.GameManager;
import Controller.InteractionManager;
import Models.Piece;
import Models.Rook;
import Models.Bishop;
import Models.Queen;
import Models.King;
import Models.Knight;
import Models.Pawn;
import java.util.Scanner;
import java.util.ArrayList;

public class ConsoleIO {
    private final GameManager gameManager;
    private final InteractionManager interactionManager;
    private final Scanner scanner;

    public ConsoleIO(GameManager gameManager, InteractionManager interactionManager) {
        this.gameManager = gameManager;
        this.interactionManager = interactionManager;
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        readAndSetupBoard();
        readAndExecuteCommands();
        scanner.close();
    }

    private void readAndSetupBoard() {
        ArrayList<String[]> rowsList = new ArrayList<>();
        boolean boardStarted = false;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.equalsIgnoreCase("Commands:")) break;
            if (line.equalsIgnoreCase("Board:")) {
                boardStarted = true;
                continue;
            }
            if (line.isEmpty()) {
                if (boardStarted) break;
                continue;
            }

            String[] tokens = line.split("\\s+");
            rowsList.add(tokens);
        }

        if (rowsList.isEmpty()) return;

        int numRows = rowsList.size();
        int numCols = rowsList.get(0).length;

        gameManager.initializeBoard(numRows, numCols);

        for (int i = 0; i < numRows; i++) {
            String[] rowTokens = rowsList.get(i);
            for (int j = 0; j < numCols; j++) {
                Piece piece = createPieceFromToken(rowTokens[j]);
                if (piece != null) {
                    gameManager.getBoard().setPieceAt(i, j, piece);
                }
            }
        }
    }

    private void readAndExecuteCommands() {
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("click")) {
                String[] parts = line.split("\\s+");
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                interactionManager.handleClick(x, y);

            } else if (line.startsWith("wait")) {
                String[] parts = line.split("\\s+");
                int ms = Integer.parseInt(parts[1]);
                gameManager.handleWait(ms);

            } else if (line.startsWith("jump")) {
                String[] parts = line.split("\\s+");
                int row = Integer.parseInt(parts[1]);
                int col = Integer.parseInt(parts[2]);
                interactionManager.handleJump(row, col);

            } else if (line.equalsIgnoreCase("print board")) {
                printBoardToConsole();
            }
        }
    }

    private void printBoardToConsole() {
        String[][] currentBoard = gameManager.getUpdatedBoardMatrix();

        for (int i = 0; i < currentBoard.length; i++) {
            for (int j = 0; j < currentBoard[i].length; j++) {
                System.out.print(currentBoard[i][j]);
                if (j < currentBoard[i].length - 1) {
                    System.out.print(" ");
                }
            }
            System.out.println();
        }
    }

    private Piece createPieceFromToken(String token) {
        if (token.equals(".")) return null;
        char color = token.charAt(0);
        char type = token.charAt(1);
        switch (type) {
            case 'R': return new Rook(color);
            case 'B': return new Bishop(color);
            case 'Q': return new Queen(color);
            case 'K': return new King(color);
            case 'N': return new Knight(color);
            case 'P': return new Pawn(color);
            default: return null;
        }
    }
}