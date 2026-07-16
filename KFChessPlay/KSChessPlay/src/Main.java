import view.*;
import engine.GameManager;
import controller.GameController;
import input.CoordinateParser;
import io.CsvBoardLoader;
import graphics.Image;
import models.Board;
import models.Piece;
import models.Position;
import input.BoardMapper;

import java.io.IOException;

public class Main {
    private static final String ASSETS_DIR   = "assets";
    private static final String BOARD_IMAGE  = ASSETS_DIR + "/board.png";
    private static final String BOARD_LAYOUT = ASSETS_DIR + "/board.csv";

    public static void main(String[] args) {
        Image background = new Image();
        try {
            background.read(BOARD_IMAGE);
        } catch (Exception e) {
            System.err.println("Error loading board image: " + e.getMessage());
            return;
        }

        BoardMapper  mapper       = new BoardMapper();
        SpriteLoader spriteLoader = new SpriteLoader();
        ImgRenderer  renderer     = new ImgRenderer(spriteLoader, background, mapper);

        GameManager    gameManager = new GameManager();
        GameController controller  = new GameController(gameManager);
        loadInitialBoard(gameManager);

        GameWindow window = new GameWindow(background);

        window.onClick((x, y) -> {
            Position pos = CoordinateParser.parseClick(
                    x, y,
                    gameManager.getBoard().getLength(),
                    gameManager.getBoard().getCols());
            if (pos != null) controller.handleInput(pos.getRow(), pos.getCol());
        });

        window.onRightClick((x, y) -> {
            Position pos = CoordinateParser.parseClick(
                    x, y,
                    gameManager.getBoard().getLength(),
                    gameManager.getBoard().getCols());
            if (pos != null) controller.requestJump(pos.getRow(), pos.getCol());
        });

        GameLoop[] loopHolder = new GameLoop[1];
        loopHolder[0] = new GameLoop(gameManager, controller, renderer, window, 60);
        loopHolder[0].start();

        window.setRestartCallback(() -> {
            if (loopHolder[0] != null) loopHolder[0].stop();
            gameManager.reset();
            loadInitialBoard(gameManager);
            controller.reset();
            window.resetHistoryTables();
            loopHolder[0] = new GameLoop(gameManager, controller, renderer, window, 60);
            loopHolder[0].start();
        });

        window.setVisible(true);
    }

    private static void loadInitialBoard(GameManager gameManager) {
        try {
            Board loaded = new CsvBoardLoader().load(BOARD_LAYOUT);
            gameManager.initializeBoard(loaded.getLength(), loaded.getCols());
            for (int r = 0; r < loaded.getLength(); r++) {
                for (int c = 0; c < loaded.getCols(); c++) {
                    Piece p = loaded.getPieceAt(new Position(r, c));
                    if (p != null) gameManager.addPieceToBoard(r, c, p);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load board, starting empty 8x8: " + e.getMessage());
            gameManager.initializeBoard(8, 8);
        }
    }
}
