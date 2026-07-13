package models;

public class Board {
    private final Piece[][] grid;
    private final int rows;
    private final int cols;

    public Board(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.grid = new Piece[rows][cols];
    }

    public int getLength() { return rows; }
    public int getCols() { return cols; }

    public boolean isPositionOnBoard(Position pos) {
        return pos.getRow() >= 0 && pos.getRow() < rows && pos.getCol() >= 0 && pos.getCol() < cols;
    }

    public Piece getPieceAt(Position pos) {
        if (!isPositionOnBoard(pos)) return null;
        return grid[pos.getRow()][pos.getCol()];
    }

    public void setPieceAt(Position pos, Piece piece) {
        if (isPositionOnBoard(pos)) {
            grid[pos.getRow()][pos.getCol()] = piece;
        }
    }

    // הדפסה בטוחה: מחזירה מטריצת מחרוזות חדשה לחלוטין לצורכי renderer בלבד
    public String[][] getReadOnlyMatrixView() {
        String[][] view = new String[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                Piece p = grid[i][j];
                view[i][j] = (p == null) ? "." : "" + p.getColor() + p.getType();
            }
        }
        return view;
    }
}