package io;

import models.Board;
import models.Piece;
import models.Position;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CsvBoardLoader {

    public Board load(String path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                rows.add(line.split(","));
            }
        }

        int numRows = rows.size();
        int numCols = numRows > 0 ? rows.get(0).length : 0;
        Board board = new Board(numRows, numCols);

        for (int r = 0; r < numRows; r++) {
            String[] tokens = rows.get(r);
            for (int c = 0; c < tokens.length; c++) {
                String token = tokens[c].trim();
                if (token.isEmpty() || token.equals(".")) continue;

                char type = token.charAt(0);
                char color = Character.toLowerCase(token.charAt(1));
                board.setPieceAt(new Position(r, c), new Piece(color, type));
            }
        }
        return board;
    }
}
