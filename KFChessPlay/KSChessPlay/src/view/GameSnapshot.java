package view;

import models.Position;

import java.util.List;

// מחזיק את כל מה שצריך כדי לצייר מסך בודד
public class GameSnapshot {
    private final List<PieceSnapshot> pieces;
    private final boolean isGameOver;
    private final Position selectedPosition;
    private final List<Position> legalMoves;

    public GameSnapshot(List<PieceSnapshot> pieces, boolean isGameOver,
                        Position selectedPosition, List<Position> legalMoves) {
        this.pieces = pieces;
        this.isGameOver = isGameOver;
        this.selectedPosition = selectedPosition;
        this.legalMoves = legalMoves;
    }

    public List<PieceSnapshot> getPieces() { return pieces; }
    public boolean isGameOver() { return isGameOver; }
    public Position getSelectedPosition() { return selectedPosition; }
    public List<Position> getLegalMoves() { return legalMoves; }
}