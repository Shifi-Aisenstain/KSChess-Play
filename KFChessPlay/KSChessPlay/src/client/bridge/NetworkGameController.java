package client.bridge;

import client.net.ServerConnection;
import client.net.ServerMessageListener;
import models.Position;
import shared.eventbus.EventBus;
import shared.protocol.AlgebraicNotation;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.CountdownPayload;
import shared.protocol.payload.EmptyPayload;
import shared.protocol.payload.ErrorPayload;
import shared.protocol.payload.GameEventPayload;
import shared.protocol.payload.GameOverPayload;
import shared.protocol.payload.JumpCommandPayload;
import shared.protocol.payload.LoginPayload;
import shared.protocol.payload.LoginResultPayload;
import shared.protocol.payload.MatchFoundPayload;
import shared.protocol.payload.MoveCommandPayload;
import shared.protocol.payload.RoomActionPayload;
import shared.protocol.payload.RoomStatusPayload;
import shared.protocol.payload.SelectPayload;
import shared.protocol.payload.StateUpdatePayload;
import view.GameSnapshot;
import view.PieceSnapshot;
import view.SnapshotSource;

import java.util.function.Consumer;

/**
 * Client-side hub: the single place that knows both "how to talk to the
 * server" (outgoing requests) and "what the server just said" (incoming
 * {@link Message}s). Everything else - {@code Main}, {@code HomeScreen},
 * {@code GameWindow} - only sees callbacks/getters, never the socket or the
 * wire protocol directly. Also doubles as the {@link SnapshotSource} that
 * {@code view.GameLoop} renders from every frame.
 */
public final class NetworkGameController implements ServerMessageListener, SnapshotSource {
    private final ServerConnection connection;
    private final EventBus localEventBus;

    private volatile GameSnapshot latestSnapshot;
    private volatile int boardRows = 8;
    private volatile int boardCols = 8;
    private volatile char yourColor = 's';
    private volatile String whiteUsername = "White";
    private volatile String blackUsername = "Black";
    private volatile Position selectedPosition;

    private Consumer<LoginResultPayload> onLoginResult;
    private Runnable onPlayWaiting;
    private Consumer<MatchFoundPayload> onMatchFound;
    private Consumer<String> onPlayNotFound;
    private Consumer<RoomStatusPayload> onRoomCreated;
    private Consumer<RoomStatusPayload> onRoomJoined;
    private Consumer<String> onRoomError;
    private Runnable onFirstStateUpdate;
    private Consumer<CountdownPayload> onDisconnectCountdown;
    private Consumer<GameOverPayload> onGameOver;
    private Consumer<String> onServerError;

    public NetworkGameController(ServerConnection connection, EventBus localEventBus) {
        this.connection = connection;
        this.localEventBus = localEventBus;
    }

    public void setOnLoginResult(Consumer<LoginResultPayload> cb) { this.onLoginResult = cb; }
    public void setOnPlayWaiting(Runnable cb) { this.onPlayWaiting = cb; }
    public void setOnMatchFound(Consumer<MatchFoundPayload> cb) { this.onMatchFound = cb; }
    public void setOnPlayNotFound(Consumer<String> cb) { this.onPlayNotFound = cb; }
    public void setOnRoomCreated(Consumer<RoomStatusPayload> cb) { this.onRoomCreated = cb; }
    public void setOnRoomJoined(Consumer<RoomStatusPayload> cb) { this.onRoomJoined = cb; }
    public void setOnRoomError(Consumer<String> cb) { this.onRoomError = cb; }
    public void setOnFirstStateUpdate(Runnable cb) { this.onFirstStateUpdate = cb; }
    public void setOnDisconnectCountdown(Consumer<CountdownPayload> cb) { this.onDisconnectCountdown = cb; }
    public void setOnGameOver(Consumer<GameOverPayload> cb) { this.onGameOver = cb; }
    public void setOnServerError(Consumer<String> cb) { this.onServerError = cb; }

    // ---- outgoing requests -------------------------------------------------

    public void requestLogin(String username, String password) {
        connection.send(MessageType.LOGIN, new LoginPayload(username, password));
    }

    public void requestPlay() {
        connection.send(MessageType.PLAY_REQUEST, EmptyPayload.INSTANCE);
    }

    public void cancelPlay() {
        connection.send(MessageType.PLAY_CANCEL, EmptyPayload.INSTANCE);
    }

    public void requestCreateRoom() {
        connection.send(MessageType.ROOM_CREATE, EmptyPayload.INSTANCE);
    }

    public void requestJoinRoom(String roomId) {
        connection.send(MessageType.ROOM_JOIN, new RoomActionPayload(roomId));
    }

    public void resign() {
        connection.send(MessageType.RESIGN, EmptyPayload.INSTANCE);
    }

    /** Wired to {@code GameWindow.onClick}. First click selects; second sends the move. Server re-validates both. */
    public void handleBoardClick(int row, int col) {
        Position clicked = new Position(row, col);

        if (selectedPosition == null) {
            selectedPosition = clicked;
            connection.send(MessageType.SELECT, new SelectPayload(true, row, col));
            return;
        }
        if (selectedPosition.equals(clicked)) {
            selectedPosition = null;
            connection.send(MessageType.SELECT, new SelectPayload(false, 0, 0));
            return;
        }

        char pieceType = findPieceTypeAt(selectedPosition);
        String command = AlgebraicNotation.format(yourColor, pieceType, selectedPosition, clicked, boardRows);
        connection.send(MessageType.MOVE, new MoveCommandPayload(command));

        selectedPosition = null;
        connection.send(MessageType.SELECT, new SelectPayload(false, 0, 0));
    }

    /** Wired to {@code GameWindow.onRightClick} - the dodge/jump-in-place mechanic. */
    public void handleBoardRightClick(int row, int col) {
        connection.send(MessageType.JUMP, new JumpCommandPayload(row, col));
    }

    // ---- incoming messages --------------------------------------------------

    @Override
    public void onMessage(Message message) {
        switch (message.getType()) {
            case LOGIN_RESULT -> deliver(onLoginResult, MessageCodec.decodePayload(message, LoginResultPayload.class));
            case PLAY_WAITING -> { if (onPlayWaiting != null) onPlayWaiting.run(); }
            case MATCH_FOUND -> deliver(onMatchFound, MessageCodec.decodePayload(message, MatchFoundPayload.class));
            case PLAY_NOT_FOUND -> deliver(onPlayNotFound, MessageCodec.decodePayload(message, ErrorPayload.class).message);
            case ROOM_CREATED -> deliver(onRoomCreated, MessageCodec.decodePayload(message, RoomStatusPayload.class));
            case ROOM_JOINED -> deliver(onRoomJoined, MessageCodec.decodePayload(message, RoomStatusPayload.class));
            case STATE_UPDATE -> handleStateUpdate(MessageCodec.decodePayload(message, StateUpdatePayload.class));
            case GAME_EVENT -> localEventBus.publish(MessageCodec.decodePayload(message, GameEventPayload.class));
            case DISCONNECT_COUNTDOWN -> deliver(onDisconnectCountdown, MessageCodec.decodePayload(message, CountdownPayload.class));
            case GAME_OVER -> deliver(onGameOver, MessageCodec.decodePayload(message, GameOverPayload.class));
            case ERROR -> deliver(onServerError, MessageCodec.decodePayload(message, ErrorPayload.class).message);
            case PONG -> { /* heartbeat acknowledged, nothing to do */ }
            default -> { /* client never receives request-only types */ }
        }
    }

    private void handleStateUpdate(StateUpdatePayload payload) {
        boolean firstUpdate = (latestSnapshot == null);
        this.boardRows = payload.boardRows;
        this.boardCols = payload.boardCols;
        this.yourColor = payload.yourColor;
        this.whiteUsername = payload.whiteUsername;
        this.blackUsername = payload.blackUsername;
        this.latestSnapshot = payload.snapshot;
        if (firstUpdate && onFirstStateUpdate != null) onFirstStateUpdate.run();
    }

    private char findPieceTypeAt(Position pos) {
        if (latestSnapshot == null) return 'P';
        for (PieceSnapshot p : latestSnapshot.getPieces()) {
            if (Math.round(p.x) == pos.getCol() && Math.round(p.y) == pos.getRow()) return p.type;
        }
        return 'P';
    }

    private <T> void deliver(Consumer<T> callback, T value) {
        if (callback != null) callback.accept(value);
    }

    @Override
    public GameSnapshot getLatestSnapshot() { return latestSnapshot; }

    public char getYourColor() { return yourColor; }
    public int getBoardRows() { return boardRows; }
    public int getBoardCols() { return boardCols; }
    public String getWhiteUsername() { return whiteUsername; }
    public String getBlackUsername() { return blackUsername; }
}
