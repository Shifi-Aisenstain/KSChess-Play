package IO;

import Controller.GameManager;
import Controller.InteractionManager;
import Models.Piece;
import Models.PieceFactory;
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

        // 🔥 בדיקת אחידות רוחב השורות (טסט 5 ו-11)
        for (String[] rowTokens : rowsList) {
            if (rowTokens.length != numCols) {
                System.out.println("ERROR ROW_WIDTH_MISMATCH");
                System.exit(0);
            }
        }

        gameManager.initializeBoard(numRows, numCols);

        for (int i = 0; i < numRows; i++) {
            String[] rowTokens = rowsList.get(i);
            for (int j = 0; j < numCols; j++) {
                String token = rowTokens[j];

                // 🔥 בדיקת טוקן לא מוכר (טסט 4 ו-10)
                if (!token.equals(".")) {
                    Piece piece = PieceFactory.createPiece(token);
                    if (piece == null) {
                        System.out.println("ERROR UNKNOWN_TOKEN");
                        System.exit(0);
                    }
                    gameManager.addPieceToBoard(i, j, piece);
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
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                // ✅ נקי! מעביר את ה-x וה-y הגולמיים, בלי לבצע מתמטיקה בתוך ה-IO
                interactionManager.handleJump(x, y);

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
}