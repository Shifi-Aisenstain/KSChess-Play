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
import java.util.LinkedHashMap;
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
 * to every room member (spectators included) over {@link GameUpdateSink}.
 *
 * <p>This is the one authoritative game engine (PLAN_split_6_services.md §3.1) - in production it
 * runs inside {@code GameServerShardMain}, a separate process from the client sockets, so it doesn't
 * talk to a {@code Room} directly: membership (who's white/black/spectator) is tracked in
 * {@link #roles}, seeded at construction and grown via {@link #onMemberJoined}, and outgoing traffic
 * goes through the injected {@link GameUpdateSink}. {@code server.shard.GameShardRuntime} is the only
 * thing that calls into this class in production; test code that wants the real engine in-process
 * can construct it directly with any {@link GameUpdateSink} (e.g. a small test double - see
 * {@code GameSessionConcurrencyTest}).
 *
 * <p>Since PLAN_split_6_services.md §3.2 (the WS Gateway extraction), disconnect/reconnect handling
 * ({@code DisconnectWatchdog}) moved out of this class entirely: it's a per-socket concern that now
 * lives with the sockets, in {@code server.gateway.GatewayRoomSession} - a disconnect there results
 * in an ordinary RESIGN command arriving here (see {@link #forceResignDueToDisconnect}), same as
 * before, just no longer via a watchdog registered on this object directly.
 *
 * <p><b>Thread safety.</b> {@code handleMove}/{@code handleJump} are called from whichever
 * bus-command-handling thread received the message - both players' commands can arrive
 * concurrently - while the dedicated {@code ticker} thread drives {@link #tick()} independently
 * every {@link #TICK_MS}. {@code GameManager}, {@code RealTimeArbiter} and {@code Board} are
 * plain, unsynchronized objects, so touching them from more than one thread at once is a race
 * condition (this used to be able to corrupt {@code RealTimeArbiter}'s active-event list or read
 * a half-updated board). Every access to {@code gameManager} - from a handler thread or the
 * ticker - is therefore confined to a {@code synchronized (gameStateLock)} block. The lock is
 * scoped to one {@code GameSession} (i.e. one room's game) rather than anything wider, so
 * unrelated rooms never contend with each other, and outgoing traffic (the actual
 * {@link GameUpdateSink} calls) is done outside the lock so a slow publish/send can't stall a
 * move being processed in another room's - or even this room's next - tick. {@code selections}
 * stays a {@link ConcurrentHashMap} since it's a simple key/value store read by the ticker and
 * written by handler threads, which {@code ConcurrentHashMap} already makes safe without any extra
 * coordination.
 */
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
    private final UserRepository userRepository;

    /** Every room member who needs a personalized STATE_UPDATE tick, including spectators - seeded
     *  with white/black at construction, grown by {@link #onMemberJoined}. Not backed by a live
     *  {@code Room} (see class javadoc); {@code ConcurrentHashMap} for the same reader/writer-thread
     *  reason {@code Room.roles} used to be. */
    private final Map<Long, Room.Role> roles = new ConcurrentHashMap<>();
    private final Map<Long, Position> selections = new ConcurrentHashMap<>();
    private final Object gameStateLock = new Object();
    private final Object lifecycleLock = new Object();
    private ScheduledExecutorService ticker;
    private volatile boolean over = false;

    /** Guarded by {@link #gameStateLock}. True whenever something happened since the last tick
     *  that clients need to see (an accepted move/jump, or a selection change) - see {@link #tick()}. */
    private boolean dirty = true;

    public GameSession(String roomId, GameUpdateSink updateSink, User white, User black,
                        EventBus eventBus, RatingService ratingService, UserRepository userRepository) {
        this.roomId = roomId;
        this.updateSink = updateSink;
        this.white = white;
        this.black = black;
        this.eventBus = eventBus;
        this.ratingService = ratingService;
        this.userRepository = userRepository;
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

    /** A member (almost always a spectator - white/black are already known at construction) joined
     *  the room after the game already started, so future personalized STATE_UPDATE ticks need to
     *  include them too. Called by {@code server.shard.GameShardRuntime} on a ROOM_ROSTER command. */
    public void onMemberJoined(long userId, Room.Role role) {
        roles.put(userId, role);
        synchronized (gameStateLock) {
            dirty = true; // so the newcomer gets a STATE_UPDATE on the very next tick, not just future changes
        }
    }

    public void handleSelect(long userId, Position position) {
        synchronized (gameStateLock) {
            if (position == null) selections.remove(userId);
            else selections.put(userId, position);
            dirty = true;
        }
    }

    /** Returns false (and does nothing) if {@code userId} doesn't own the piece on the source
     *  square, or the move was otherwise rejected. */
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

    /** Called (via a RESIGN bus command with its disconnect flag set) when
     *  {@code server.gateway.DisconnectWatchdog}'s 20s countdown reaches zero. */
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
            // Whether there's anything worth telling clients about: either something happened
            // since the last tick (a move/jump/selection), or a previously-registered move/jump/
            // cooldown is still animating and needs its next interpolated frame. Checked *before*
            // handleWait so a move/jump that finishes exactly on this tick still gets one last
            // snapshot (with its settled position, and any resulting game-over) instead of being
            // silently swallowed.
            boolean hasSomethingToReport = dirty || gameManager.hasActiveEvents();
            gameManager.handleWait((int) TICK_MS);
            if (hasSomethingToReport) {
                payloads = buildPersonalizedPayloads();
                probe = gameManager.createSnapshot();
                dirty = false;
            }
        }

        if (payloads == null) return; // nothing changed and nothing was animating - skip the broadcast entirely

        // Actual outgoing traffic happens after releasing the lock, so a slow/blocked sink can't
        // hold up move/jump processing for this room (or the next tick).
        for (Map.Entry<Long, StateUpdatePayload> entry : payloads.entrySet()) {
            updateSink.sendTo(entry.getKey(), MessageType.STATE_UPDATE, entry.getValue());
        }

        if (probe.isGameOver()) {
            String winnerColor = "White".equalsIgnoreCase(probe.getWinner()) ? "WHITE"
                    : "Black".equalsIgnoreCase(probe.getWinner()) ? "BLACK" : null;
            finishGame(winnerColor, "checkmate");
        }
    }

    /** Must be called while holding {@link #gameStateLock}. Builds, but does not send, one payload per room member. */
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
        userRepository.updateElo(white.getId(), update.whiteNewElo);
        userRepository.updateElo(black.getId(), update.blackNewElo);

        eventBus.publish(new GameEndedEvent(roomId, winnerColor, reason, white.getUsername(), black.getUsername()));
        updateSink.broadcastToAll(MessageType.GAME_OVER, new GameOverPayload(
                roomId, winnerColor, reason,
                update.whiteDelta, update.blackDelta, update.whiteNewElo, update.blackNewElo,
                white.getUsername(), black.getUsername()));
    }
}
