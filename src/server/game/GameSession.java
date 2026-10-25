package server.game;

import controller.MoveCommand;
import engine.GameManager;
import io.CsvBoardLoader;
import models.Board;
import models.Piece;
import models.Position;
import server.auth.User;
import server.history.GameHistoryRecorder;
import server.rating.EloWriter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class GameSession {
    private static final long TICK_MS = 50;
    private static final String BOARD_CSV_PATH = "assets/board.csv";

    private final String roomId;
    private final GameUpdateSink updateSink;
    private final GameManager gameManager;
    private final User white;
    private final User black;
    private final EventBus eventBus;
    private final RatingService ratingService;
    private final EloWriter eloWriter;
    private final GameHistoryRecorder historyRecorder;

    private final Map<Long, Room.Role> roles = new ConcurrentHashMap<>();
    private final Map<Long, Position> selections = new ConcurrentHashMap<>();
    private final Object gameStateLock = new Object();
    private final Object lifecycleLock = new Object();
    private ScheduledExecutorService ticker;
    private volatile boolean over = false;

    private boolean dirty = true;

    public GameSession(String roomId, GameUpdateSink updateSink, User white, User black,
                        EventBus eventBus, RatingService ratingService, EloWriter eloWriter,
                        GameHistoryRecorder historyRecorder) {
        this.roomId = roomId;
        this.updateSink = updateSink;
        this.white = white;
        this.black = black;
        this.eventBus = eventBus;
        this.ratingService = ratingService;
        this.eloWriter = eloWriter;
        this.historyRecorder = historyRecorder;
        this.gameManager = new GameManager(roomId, eventBus);
        this.roles.put(white.getId(), Room.Role.WHITE);
        this.roles.put(black.getId(), Room.Role.BLACK);
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

    public void onMemberJoined(long userId, Room.Role role) {
        roles.put(userId, role);
        synchronized (gameStateLock) {
            dirty = true;
        }
    }

    public void handleSelect(long userId, Position position) {
        synchronized (gameStateLock) {
            if (position == null) selections.remove(userId);
            else selections.put(userId, position);
            dirty = true;
        }
    }

    public boolean handleMove(long userId, Position from, Position to) {
        synchronized (gameStateLock) {
            if (over || !ownsColorAt(userId, from)) return false;
            boolean registered = gameManager.requestMove(new MoveCommand(from, to));
            if (registered) dirty = true;
            return registered;
        }
    }

    public boolean handleJump(long userId, Position at) {
        synchronized (gameStateLock) {
            if (over || !ownsColorAt(userId, at)) return false;
            boolean registered = gameManager.requestJump(at);
            if (registered) dirty = true;
            return registered;
        }
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

        Map<Long, StateUpdatePayload> payloads = null;
        GameSnapshot probe = null;
        synchronized (gameStateLock) {

            boolean hasSomethingToReport = dirty || gameManager.hasActiveEvents();
            gameManager.handleWait((int) TICK_MS);
            if (hasSomethingToReport) {
                payloads = buildPersonalizedPayloads();
                probe = gameManager.createSnapshot();
                dirty = false;
            }
        }

        if (payloads == null) return;

        for (Map.Entry<Long, StateUpdatePayload> entry : payloads.entrySet()) {
            updateSink.sendTo(entry.getKey(), MessageType.STATE_UPDATE, entry.getValue());
        }

        if (probe.isGameOver()) {
            String winnerColor = "White".equalsIgnoreCase(probe.getWinner()) ? "WHITE"
                    : "Black".equalsIgnoreCase(probe.getWinner()) ? "BLACK" : null;
            finishGame(winnerColor, "checkmate");
        }
    }

    private Map<Long, StateUpdatePayload> buildPersonalizedPayloads() {
        Map<Long, StateUpdatePayload> payloads = new LinkedHashMap<>();
        Board board = gameManager.getBoard();
        for (Map.Entry<Long, Room.Role> entry : roles.entrySet()) {
            long userId = entry.getKey();
            char color = entry.getValue() == Room.Role.WHITE ? 'w' : entry.getValue() == Room.Role.BLACK ? 'b' : 's';
            Position selected = selections.get(userId);
            List<Position> legalMoves = selected != null
                    ? gameManager.getLegalDestinations(selected)
                    : Collections.emptyList();
            GameSnapshot snapshot = gameManager.createSnapshot(selected, legalMoves);
            payloads.put(userId, new StateUpdatePayload(roomId, color, board.getLength(), board.getCols(), snapshot,
                    white.getUsername(), black.getUsername()));
        }
        return payloads;
    }

    private void finishGame(String winnerColor, String reason) {
        synchronized (lifecycleLock) {
            if (over) return;
            over = true;
        }
        if (ticker != null) ticker.shutdown();

        RatingService.EloUpdate update = ratingService.computeUpdate(white.getElo(), black.getElo(), winnerColor);
        eloWriter.updateElo(white.getId(), update.whiteNewElo);
        eloWriter.updateElo(black.getId(), update.blackNewElo);
        historyRecorder.recordGameResult(roomId, white.getId(), black.getId(), white.getUsername(), black.getUsername(),
                winnerColor, reason, update.whiteDelta, update.blackDelta);

        eventBus.publish(new GameEndedEvent(roomId, winnerColor, reason, white.getUsername(), black.getUsername()));
        updateSink.broadcastToAll(MessageType.GAME_OVER, new GameOverPayload(
                roomId, winnerColor, reason,
                update.whiteDelta, update.blackDelta, update.whiteNewElo, update.blackNewElo,
                white.getUsername(), black.getUsername()));
    }
}
