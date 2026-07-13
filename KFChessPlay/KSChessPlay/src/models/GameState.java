package models;

public class GameState {
    private boolean isGameOver;
    private String currentTurn; // למשל "WHITE" או "BLACK"
    private Board board;

    public GameState(Board board) {
        this.board = board;
        this.isGameOver = false;
        this.currentTurn = "WHITE"; // ברירת מחדל
    }

    // Getters & Setters
    public boolean isGameOver() {
        return isGameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.isGameOver = gameOver;
    }

    public String getCurrentTurn() {
        return currentTurn;
    }

    public void switchTurn() {
        this.currentTurn = this.currentTurn.equals("WHITE") ? "BLACK" : "WHITE";
    }

    public Board getBoard() {
        return board;
    }
}