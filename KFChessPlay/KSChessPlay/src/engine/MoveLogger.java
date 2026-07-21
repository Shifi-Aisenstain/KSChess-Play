package engine;

import models.Board;
import models.Piece;
import models.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MoveLogger {
    private int scoreWhite = 0;
    private int scoreBlack = 0;
    private final List<String> whiteMoveHistory = new ArrayList<>();
    private final List<String> blackMoveHistory = new ArrayList<>();
    private long gameStartMs = System.currentTimeMillis();

    public void recordMove(Position src, Position dest, Piece movedPiece, Piece capturedPiece, Board board) {
        if (capturedPiece != null) {
            awardCaptureScore(movedPiece.getColor(), capturedPiece.getType());
        }
        String sideEntry = formatElapsedTime(System.currentTimeMillis() - gameStartMs)
                + "|" + buildMoveNotation(movedPiece, dest, capturedPiece, board);
        if (movedPiece.getColor() == 'w') {
            whiteMoveHistory.add(sideEntry);
        } else {
            blackMoveHistory.add(sideEntry);
        }
    }

    private void awardCaptureScore(char capturingColor, char capturedType) {
        int value = pieceValue(capturedType);
        if (value <= 0) return;
        if (capturingColor == 'w') scoreWhite += value;
        else scoreBlack += value;
    }

    private String buildMoveNotation(Piece movedPiece, Position dest, Piece capturedPiece, Board board) {
        String square = squareName(dest, board);
        if (capturedPiece != null && capturedPiece.getType() != 'K') {
            return pieceName(movedPiece.getType()) + "x" + square;
        }
        return pieceName(movedPiece.getType()) + square;
    }

    private static int pieceValue(char type) {
        switch (type) {
            case 'N': case 'B': return 3;
            case 'R': return 5;
            case 'Q': return 9;
            case 'P': return 1;
            default:  return 0;
        }
    }

    private static String pieceName(char type) {
        switch (type) {
            case 'P': return "P";
            case 'N': return "N";
            case 'B': return "B";
            case 'R': return "R";
            case 'Q': return "Q";
            case 'K': return "K";
            default:  return String.valueOf(type);
        }
    }

    private static String squareName(Position p, Board board) {
        char file = (char) ('a' + p.getCol());
        int rank = (board != null) ? board.getLength() - p.getRow() : p.getRow() + 1;
        return "" + file + rank;
    }

    private static String formatElapsedTime(long elapsedMs) {
        long totalSec = elapsedMs / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        long ms  = elapsedMs % 1000;
        return String.format("%02d:%02d.%03d", min, sec, ms);
    }

    public int getScoreWhite() { return scoreWhite; }
    public int getScoreBlack() { return scoreBlack; }
    public List<String> getWhiteMoveHistory() { return Collections.unmodifiableList(whiteMoveHistory); }
    public List<String> getBlackMoveHistory() { return Collections.unmodifiableList(blackMoveHistory); }

    /** The "MM:SS.mmm|Notation" entry just recorded for that color, or "" if none yet. Used to publish move-log updates onto the event bus without re-deriving the format. */
    public String getLastEntry(char color) {
        List<String> history = (color == 'w') ? whiteMoveHistory : blackMoveHistory;
        return history.isEmpty() ? "" : history.get(history.size() - 1);
    }

    public void reset() {
        scoreWhite = 0;
        scoreBlack = 0;
        whiteMoveHistory.clear();
        blackMoveHistory.clear();
        gameStartMs = System.currentTimeMillis();
    }
}
