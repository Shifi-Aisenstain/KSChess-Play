//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import java.util.Scanner;
import java.util.ArrayList;

public class Main {

        private static String[][] board;
        private static int numRows = 0;
        private static int numCols = 0;

        private static int selectedRow = -1;
        private static int selectedCol = -1;

        private static long gameClockMs = 0;

        private static boolean isValidToken(String token) {
            if (token.equals(".")) {
                return true;
            }
            if (token.length() == 2) {
                char color = token.charAt(0);
                char piece = token.charAt(1);
                boolean validColor = (color == 'w' || color == 'b');
                boolean validPiece = (piece == 'K' || piece == 'Q' || piece == 'R' || piece == 'B' || piece == 'N' || piece == 'P');
                return validColor && validPiece;
            }
            return false;
        }

        private static void printCurrentBoard() {
            for (int i = 0; i < numRows; i++) {
                for (int j = 0; j < numCols; j++) {
                    System.out.print(board[i][j]);
                    if (j < numCols - 1) {
                        System.out.print(" ");
                    }
                }
                System.out.println();
            }
        }

    private static boolean isPathClear(String[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        int rowStep = Integer.compare(toRow, fromRow);
        int colStep = Integer.compare(toCol, fromCol);

        int currentRow = fromRow + rowStep;
        int currentCol = fromCol + colStep;

        while (currentRow != toRow || currentCol != toCol) {
            if (!board[currentRow][currentCol].equals(".")) {
                return false;
            }
            currentRow += rowStep;
            currentCol += colStep;
        }
        return true;
    }

        private static boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
            if (fromRow == toRow && fromCol == toCol)
                return false;
            char piece = board[fromRow][fromCol].charAt(1);
            switch (piece) {
                case 'R':
                    //צריח
                    if (fromRow != toRow && fromCol != toCol) {
                        return false;
                    }
                    return isPathClear(board, fromRow, fromCol, toRow, toCol);

                case 'B':
                    //  רץ
                    if (Math.abs(fromRow - toRow) != Math.abs(fromCol - toCol)) {
                        return false;
                    }
                    return isPathClear(board, fromRow, fromCol, toRow, toCol);

                case 'Q':
                    // מלכה
                    boolean validRookMove = (fromRow == toRow || fromCol == toCol);
                    boolean validBishopMove = (Math.abs(fromRow - toRow) == Math.abs(fromCol - toCol));

                    if (!validRookMove && !validBishopMove) {
                        return false;
                    }

                    return isPathClear(board, fromRow, fromCol, toRow, toCol);

                case 'K':
                    // מלך
                    return Math.abs(fromRow - toRow) <= 1 && Math.abs(fromCol - toCol) <= 1;

                case 'N':
                    // פרש
                    return (Math.abs(fromRow - toRow) == 2 && Math.abs(fromCol - toCol) == 1) ||
                            (Math.abs(fromRow - toRow) == 1 && Math.abs(fromCol - toCol) == 2);
                case 'P':
                    // חייל
                    String fullPiece = board[fromRow][fromCol];
                    char color = fullPiece.charAt(0);
                    int rowDiff = toRow - fromRow;
                    int colDiff = Math.abs(toCol - fromCol);
                    if (color == 'w') {
                        if (rowDiff == -1 && colDiff == 0 && board[toRow][toCol].equals(".")) {
                            return true;
                        }
                        if (rowDiff == -1 && colDiff == 1 && board[toRow][toCol].startsWith("b")) {
                            return true;
                        }
                    }
                    if (color == 'b') {
                        if (rowDiff == 1 && colDiff == 0 && board[toRow][toCol].equals(".")) {
                            return true;
                        }
                        if (rowDiff == 1 && colDiff == 1 && board[toRow][toCol].startsWith("w")) {
                            return true;
                        }

                    }
                    return false;
                default:
                    return false;
            }

        }


        private static void handleClick(int x, int y) {
            int col = x / 100;
            int row = y / 100;

            if (row < 0 || row >= numRows || col < 0 || col >= numCols) {
                return;
            }

            String clickedCell = board[row][col];

            if (!clickedCell.equals(".")) {
                if (selectedRow == -1) {
                    selectedRow = row;
                    selectedCol = col;
                } else {
                    String selectedCell = board[selectedRow][selectedCol];
                    if (selectedCell.charAt(0) == clickedCell.charAt(0)) {
                        selectedRow = row;
                        selectedCol = col;
                    } else {
                        if (isValidMove(selectedRow, selectedCol, row, col)) {
                            board[row][col] = board[selectedRow][selectedCol];
                            board[selectedRow][selectedCol] = ".";
                            selectedRow = -1;
                            selectedCol = -1;
                        }
                    }
                }
            } else {
                if (selectedRow != -1) {
                    if (isValidMove(selectedRow, selectedCol, row, col)) {
                        board[row][col] = board[selectedRow][selectedCol];
                        board[selectedRow][selectedCol] = ".";
                        selectedRow = -1;
                        selectedCol = -1;
                    }
                }
            }
        }

        public static void main(String[] args) {
            Scanner scanner = new Scanner(System.in);
            ArrayList<String[]> rowsList = new ArrayList<>();
            boolean boardStarted = false;

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                if (line.equalsIgnoreCase("Commands:")) {
                    break;
                }

                if (line.equalsIgnoreCase("Board:")) {
                    boardStarted = true;
                    continue;
                }

                if (line.isEmpty()) {
                    if (boardStarted) break;
                    continue;
                }

                String[] tokens = line.split("\\s+");

                for (String token : tokens) {
                    if (!isValidToken(token)) {
                        System.out.println("ERROR UNKNOWN_TOKEN");
                        return;
                    }
                }

                if (!rowsList.isEmpty() && tokens.length != rowsList.get(0).length) {
                    System.out.println("ERROR ROW_WIDTH_MISMATCH");
                    return;
                }

                rowsList.add(tokens);
            }

            if (rowsList.isEmpty()) {
                return;
            }

            numRows = rowsList.size();
            numCols = rowsList.get(0).length;
            board = new String[numRows][numCols];
            for (int i = 0; i < numRows; i++) {
                board[i] = rowsList.get(i);
            }

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("click")) {
                    String[] parts = line.split("\\s+");
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);


                    handleClick(x, y);

                } else if (line.startsWith("wait")) {
                    String[] parts = line.split("\\s+");
                    int ms = Integer.parseInt(parts[1]);
                    gameClockMs += ms;

                } else if (line.equalsIgnoreCase("print board")) {
                    printCurrentBoard();
                }
            }
            scanner.close();
        }
}