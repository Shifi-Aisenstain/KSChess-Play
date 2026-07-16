package view;

import models.Position;
import java.util.Collections;
import java.util.List;

public class GameSnapshot {
    private final List<PieceSnapshot> pieces;
    private final boolean isGameOver;
    private final String winner;
    private final Position selectedPosition;
    private final List<Position> legalMoves;
    private final List<CooldownHighlight> cooldownHighlights;
    private final int scoreWhite;
    private final int scoreBlack;
    private final List<String> whiteMoveHistory;
    private final List<String> blackMoveHistory;

    public GameSnapshot(List<PieceSnapshot> pieces,
                        boolean isGameOver,
                        String winner,
                        Position selectedPosition,
                        List<Position> legalMoves,
                        List<CooldownHighlight> cooldownHighlights,
                        int scoreWhite,
                        int scoreBlack,
                        List<String> whiteMoveHistory,
                        List<String> blackMoveHistory) {
        this.pieces             = Collections.unmodifiableList(pieces);
        this.isGameOver         = isGameOver;
        this.winner             = winner;
        this.selectedPosition   = selectedPosition;
        this.legalMoves         = Collections.unmodifiableList(legalMoves);
        this.cooldownHighlights = Collections.unmodifiableList(cooldownHighlights);
        this.scoreWhite         = scoreWhite;
        this.scoreBlack         = scoreBlack;
        this.whiteMoveHistory   = Collections.unmodifiableList(whiteMoveHistory);
        this.blackMoveHistory   = Collections.unmodifiableList(blackMoveHistory);
    }

    public List<PieceSnapshot> getPieces()               { return pieces; }
    public boolean isGameOver()                          { return isGameOver; }
    public String getWinner()                            { return winner; }
    public Position getSelectedPosition()                { return selectedPosition; }
    public List<Position> getLegalMoves()                { return legalMoves; }
    public List<CooldownHighlight> getCooldownHighlights() { return cooldownHighlights; }
    public int getScoreWhite()                           { return scoreWhite; }
    public int getScoreBlack()                           { return scoreBlack; }
    public List<String> getWhiteMoveHistory()            { return whiteMoveHistory; }
    public List<String> getBlackMoveHistory()            { return blackMoveHistory; }

    public String getGameOverMessage() {
        if (!isGameOver) return "";
        return winner != null ? "Game Over — " + winner + " wins!" : "Game Over!";
    }
}
