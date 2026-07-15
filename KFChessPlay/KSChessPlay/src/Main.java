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
    private static final String ASSETS_DIR = "assets";
    private static final String BOARD_IMAGE = ASSETS_DIR + "/board.png";
    private static final String BOARD_LAYOUT = ASSETS_DIR + "/board.csv";

    public static void main(String[] args) {
        System.out.println("Starting Game...");

        GameManager gameManager = new GameManager();
        loadInitialBoard(gameManager);

        GameController controller = new GameController(gameManager);
        SpriteLoader spriteLoader = new SpriteLoader();

        // Load the background once here - it is the single source of truth
        // for this asset. (Previously both Main and GameWindow loaded a
        // background image independently, from two different, both wrong,
        // paths - that duplication was itself part of the bug.)
        Image background = new Image();
        Image canvas = new Image();
        try {
            background.read(BOARD_IMAGE);
            canvas.read(BOARD_IMAGE); // separate mutable copy used as the live frame buffer
            System.out.println("Board image loaded successfully.");
        } catch (Exception e) {
            System.err.println("Error loading board image: " + e.getMessage());
            return;
        }

        BoardMapper mapper = new BoardMapper();
        ImgRenderer renderer = new ImgRenderer(spriteLoader, background, mapper);

        GameWindow window = new GameWindow(canvas);
        window.onClick((x, y) -> {
            Position pos = CoordinateParser.parseClick(
                    x, y,
                    gameManager.getBoard().getLength(),
                    gameManager.getBoard().getCols()
            );
            if (pos != null) {
                controller.handleInput(pos.getRow(), pos.getCol());
            }
        });

        GameLoop loop = new GameLoop(gameManager, controller, renderer, canvas, window, 60);
        loop.start();

        window.setVisible(true);
        System.out.println("Window set to visible.");
    }

    private static void loadInitialBoard(GameManager gameManager) {
        try {
            Board loaded = new CsvBoardLoader().load(BOARD_LAYOUT);
            gameManager.initializeBoard(loaded.getLength(), loaded.getCols());
            for (int r = 0; r < loaded.getLength(); r++) {
                for (int c = 0; c < loaded.getCols(); c++) {
                    Piece p = loaded.getPieceAt(new Position(r, c));
                    if (p != null) {
                        gameManager.addPieceToBoard(r, c, p);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load " + BOARD_LAYOUT + ", starting with an empty 8x8 board: " + e.getMessage());
            gameManager.initializeBoard(8, 8);
        }
    }
}