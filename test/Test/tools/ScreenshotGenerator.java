package Test.tools;

import client.ui.HomeScreen;
import controller.MoveCommand;
import engine.GameManager;
import graphics.Image;
import input.BoardMapper;
import io.CsvBoardLoader;
import models.Board;
import models.Position;
import view.GameSnapshot;
import view.GameWindow;
import view.ImgRenderer;
import view.SpriteLoader;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the screenshots used in the README from the real game: a game is played in the real engine
 * ({@link GameManager}), drawn by the real renderer ({@link ImgRenderer}) and shown in the real Swing windows
 * ({@link GameWindow}, {@link HomeScreen}), and every window is painted to a PNG. No window is displayed.
 *
 * Run from the project root (the assets are read from ./assets):
 * <pre>
 *   mvn -q test-compile
 *   java -cp target/classes:target/test-classes Test.tools.ScreenshotGenerator docs/screenshots
 * </pre>
 */
public class ScreenshotGenerator {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "docs/screenshots");
        Files.createDirectories(out);

        SwingUtilities.invokeAndWait(() -> {
            try {
                gameInProgress(out.resolve("game.png"));
                gameOver(out.resolve("game-over.png"));
                homeScreen(out.resolve("home.png"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.exit(0);
    }

    // ---------------------------------------------------------------- scenarios

    /** A real-time game: pieces on the move, pieces resting after a move, a capture, the move history and the score. */
    private static void gameInProgress(Path file) throws Exception {
        GameManager game = newGame();

        // both sides open at the same time, no turns
        move(game, 6, 4, 4, 4);          // white e2-e4
        move(game, 1, 3, 3, 3);          // black d7-d5
        game.handleWait(2100);

        move(game, 7, 5, 5, 3);          // white bishop is on its way
        move(game, 0, 1, 2, 2);          // black knight
        move(game, 0, 6, 2, 5);          // black knight
        game.handleWait(3000);           // the pawns have rested

        move(game, 4, 4, 3, 3);          // white pawn takes the black pawn on d5
        game.handleWait(1100);

        move(game, 7, 6, 5, 5);          // white knight
        move(game, 1, 4, 3, 4);          // black answers with e7-e5
        game.handleWait(900);

        Position selected = new Position(7, 1);
        GameSnapshot snapshot = game.createSnapshot(selected, game.getLegalDestinations(selected));
        saveWindow(file, snapshot, "Alice", "Bob", "Alice captured a pawn", new Color(255, 224, 130));
    }

    /** The game ends when a king is captured. */
    private static void gameOver(Path file) throws Exception {
        GameManager game = new GameManager();
        game.initializeBoard(8, 8);
        add(game, 7, 3, 'w', 'K');
        add(game, 7, 4, 'w', 'Q');
        add(game, 6, 0, 'w', 'P');
        add(game, 0, 4, 'b', 'K');
        add(game, 0, 3, 'b', 'Q');
        add(game, 1, 6, 'b', 'P');
        add(game, 1, 1, 'b', 'P');

        move(game, 7, 4, 0, 4);          // the white queen takes the black king
        game.handleWait(7500);

        saveWindow(file, game.createSnapshot(), "Alice", "Bob", "Game over", new Color(239, 154, 154));
    }

    private static void homeScreen(Path file) throws Exception {
        HomeScreen home = new HomeScreen("Alice", 1264);
        home.setSize(360, 200);
        home.addNotify();
        home.validate();
        paint(home, file);
    }

    // ---------------------------------------------------------------- helpers

    private static GameManager newGame() throws Exception {
        Board start = new CsvBoardLoader().load("assets/board.csv");
        GameManager game = new GameManager();
        game.initializeBoard(start.getLength(), start.getCols());
        for (int r = 0; r < start.getLength(); r++) {
            for (int c = 0; c < start.getCols(); c++) {
                var piece = start.getPieceAt(new Position(r, c));
                if (piece != null) game.addPieceToBoard(r, c, piece);
            }
        }
        return game;
    }

    private static void add(GameManager game, int row, int col, char color, char type) {
        game.addPieceToBoard(row, col, new models.Piece(color, type));
    }

    private static void move(GameManager game, int fromRow, int fromCol, int toRow, int toCol) {
        boolean accepted = game.requestMove(new MoveCommand(new Position(fromRow, fromCol), new Position(toRow, toCol)));
        if (!accepted) {
            throw new IllegalStateException("The scripted move " + fromRow + "," + fromCol + " -> " + toRow + "," + toCol + " was rejected");
        }
    }

    private static void saveWindow(Path file, GameSnapshot snapshot, String white, String black, String banner, Color bannerColor) throws Exception {
        Image background = new Image();
        background.read("assets/board.png");
        ImgRenderer renderer = new ImgRenderer(new SpriteLoader(), background, new BoardMapper());

        GameWindow window = new GameWindow(background, white, black);
        window.updateCanvas(renderer.render(snapshot, 1.0));
        window.updateSidePanel(snapshot);
        window.flashBanner(banner, bannerColor);
        window.pack();
        paint(window, file);
    }

    private static void paint(JFrame frame, Path file) throws Exception {
        int width = frame.getContentPane().getWidth();
        int height = frame.getContentPane().getHeight();
        if (width == 0 || height == 0) {
            width = frame.getWidth();
            height = frame.getHeight();
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        frame.getContentPane().paint(g);
        g.dispose();
        javax.imageio.ImageIO.write(image, "png", new File(file.toString()));
        System.out.println("wrote " + file + " (" + width + "x" + height + ")");
    }
}
