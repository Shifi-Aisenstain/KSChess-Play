package io;

import models.Board;
import models.Piece;
import models.Position;
import java.util.ArrayList;
import java.util.List;

public class BoardParser {

    public Board parseFromString(String boardData) {
        if (boardData == null || boardData.trim().isEmpty()) {
            return new Board(0, 0);
        }

        String[] lines = boardData.split("\n");
        List<String[]> cleanedRows = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("Board:") || trimmed.isEmpty()) {
                continue;
            }
            cleanedRows.add(trimmed.split("\\s+"));
        }

        int numRows = cleanedRows.size();
        int numCols = numRows > 0 ? cleanedRows.get(0).length : 0;

        for (String[] row : cleanedRows) {
            if (row.length != numCols) {
                System.out.println("ERROR ROW_WIDTH_MISMATCH");
                return null;
            }
        }

        Board board = new Board(numRows, numCols);

        for (int r = 0; r < numRows; r++) {
            String[] rowTokens = cleanedRows.get(r);
            for (int c = 0; c < numCols; c++) {
                String token = rowTokens[c];

                if (token.equals(".")) {
                    board.setPieceAt(new Position(r, c), null);
                } else {
                    if (token.length() != 2 || (token.charAt(0) != 'w' && token.charAt(0) != 'b')) {
                        System.out.println("ERROR UNKNOWN_TOKEN");
                        return null;
                    }
                    // יצירת הכלי ומיקומו על הלוח לפי הפירוט של הטוקן (צבע וסוג)
                    char color = token.charAt(0);
                    char type = token.charAt(1);
                    board.setPieceAt(new Position(r, c), new Piece(color, type));
                }
            }
        }

        return board;
    }
}