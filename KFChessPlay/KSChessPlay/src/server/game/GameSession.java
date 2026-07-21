package server.game;

import controller.MoveCommand;
import engine.GameManager;
import io.CsvBoardLoader;
import models.Board;
import models.Piece;
import models.Position;
import server.auth.User;
import server.auth.UserRepository;
import server.rating.RatingService;
import server.rooms.Room;
import shared.eventbus.EventBus;
import shared.eventbus.events.GameEndedEvent;
import shared.eventbus.events.GameStartedEvent;
import shared.protocol.MessageType;
import shared.protocol.payload.GameOverPayload;
import shared.protocol.payload.StateUpdatePayload;
import view.GameSnapshot;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runtime for exactly one match. Wraps the reused single-player
 * {@link GameManager} and turns its "pull" model (caller decides when to
 * advance time / take a snapshot) into a "push" model: a tick thread
 * advances the simulation and streams a personalized {@link GameSnapshot}
 * to every room member (spectators included) over {@link Room#sendTo}.
 *
 * <p>Snapshots are personalized because {@code GameManager.createSnapshot}
 * takes a selected-square + legal-moves pair for click highlighting, and
 * each connected client can have a different square selected - so the
 * authoritative legality check (which square is highlighted) stays
 * server-side instead of being duplicated into client code.
 */
public final class GameSession {
    private static final long TICK_MS = 50;
    private static final String BOARD_CSV_PATH = "assets/board.csv";

    private final String roomId;
    private final Room room;
    private final GameManager gameManager;
    private final User white;
    private final User black;
    private final EventBus eventBus;
    private final RatingService ratingService;
    private final UserRepository userRepository;

    private final Map<Long, Position> selections = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    private ScheduledExecutorService ticker;
    private volatile boolean over = false;

    public GameSession(String roomId, Room room, User white, User black,
                        EventBus eventBus, RatingService ratingService, UserRepository userRepository) {
        this.roomId = roomId;
        this.room = room;
        this.white = white;
        this.black = black;
        this.eventBus = eventBus;
        this.ratingService = ratingService;
        this.userRepository = userRepository;
        this.gameManager = new GameManager(roomId, eventBus);
    }

    public void start() {
        loadInitialBoard();
        eventBus.publish(new GameStartedEvent(roomId, white.getUsername(), black.getUsername()));
        ticker = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "game-session-" + roomId));
        ticker.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    private void loadInitialBoard() {
        try {
            Board loaded = new CsvBoardLoader().load(BOARD_CSV_PATH);
            gameManager.initializeBoard(loaded.getLength(), loaded.getCols());
            for (int r = 0; r < loaded.getLength(); r++) {
                for (int c = 0; c < loaded.getCols(); c++) {
                    Piece p = loaded.getPieceAt(new Position(r, c));
                    if (p != null) gameManager.addPieceToBoard(r, c, p);
                }
            }
        } catch (IOException e) {
            System.err.println("[room " + roomId + "] could not load board, defaulting to empty 8x8: " + e.getMessage());
            gameManager.initializeBoard(8, 8);
        }
    }

    public void handleSelect(long userId, Position position) {
        if (position == null) selections.remove(userId);
        else selections.put(userId, position);
    }

    /** Returns false (and does nothing) if {@code userId} doesn't own the piece on the source square. */
    public boolean handleMove(long userId, Position from, Position to) {
        if (over || !ownsColorAt(userId, from)) return false;
        gameManager.requestMove(new MoveCommand(from, to));
        return true;
    }

    public boolean handleJump(long userId, Position at) {
        if (over || !ownsColorAt(userId, at)) return false;
        gameManager.requestJump(at);
        return true;
    }

    public void handleResign(long userId) {
        finishGame(opponentColorOf(userId), "resignation");
    }

    public void forceResignDueToDisconnect(long userId) {
        finishGame(opponentColorOf(userId), "disconnect_timeout");
    }

    public char colorOf(long userId) {
        if (userId == white.getId()) return 'w';
        if (userId == black.getId()) return 'b';
        return 's';
    }

    public String getRoomId() { return roomId; }
    public User getWhite() { return white; }
    public User getBlack() { return black; }
    public boolean isOver() { return over; }
    public int getBoardRows() { return gameManager.getBoard().getLength(); }

    private boolean ownsColorAt(long userId, Position pos) {
        char owner = gameManager.colorAt(pos);
        return owner != '\0' && owner == colorOf(userId);
    }

    private String opponentColorOf(long userId) {
        return colorOf(userId) == 'w' ? "BLACK" : "WHITE";
    }

    private void tick() {
        if (over) return;
        gameManager.handleWait((int) TICK_MS);
        broadcastPersonalizedSnapshots();

        GameSnapshot probe = gameManager.createSnapshot();
        if (probe.isGameOver()) {
            String winnerColor = "White".equalsIgnoreCase(probe.getWinner()) ? "WHITE"
                    : "Black".equalsIgnoreCase(probe.getWinner()) ? "BLACK" : null;
            finishGame(winnerColor, "checkmate");
        }
    }

    private void broadcastPersonalizedSnapshots() {
        for (Map.Entry<Long, Room.Role> entry : room.getRoles().entrySet()) {
            long userId = entry.getKey();
            char color = entry.getValue() == Room.Role.WHITE ? 'w' : entry.getValue() == Room.Role.BLACK ? 'b' : 's';
            Position selected = selections.get(userId);
            List<Position> legalMoves = selected != null
                    ? gameManager.getLegalDestinations(selected)
                    : Collections.emptyList();
            GameSnapshot snapshot = gameManager.createSnapshot(selected, legalMoves);
            Board board = gameManager.getBoard();
            room.sendTo(userId, MessageType.STATE_UPDATE,
                    new StateUpdatePayload(roomId, color, board.getLength(), board.getCols(), snapshot,
                            white.getUsername(), black.getUsername()));
        }
    }

    private void finishGame(String winnerColor, String reason) {
        synchronized (lifecycleLock) {
            if (over) return;
            over = true;
        }
        if (ticker != null) ticker.shutdown();

        RatingService.EloUpdate update = ratingService.computeUpdate(white.getElo(), black.getElo(), winnerColor);
        userRepository.updateElo(white.getId(), update.whiteNewElo);
        userRepository.updateElo(black.getId(), update.blackNewElo);

        eventBus.publish(new GameEndedEvent(roomId, winnerColor, reason, white.getUsername(), black.getUsername()));
        room.broadcastToAll(MessageType.GAME_OVER, new GameOverPayload(
                roomId, winnerColor, reason,
                update.whiteDelta, update.blackDelta, update.whiteNewElo, update.blackNewElo,
                white.getUsername(), black.getUsername()));
    }
}
