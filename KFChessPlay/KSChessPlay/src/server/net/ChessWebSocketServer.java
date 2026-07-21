package server.net;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import server.auth.User;
import server.dispatch.MessageDispatcher;
import server.game.DisconnectWatchdog;
import server.game.GameSession;
import server.logging.ServerActivityLogger;
import server.matchmaking.MatchmakingService;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.protocol.Message;
import shared.protocol.MessageCodec;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Thin transport adapter: turns raw WebSocket callbacks into
 * {@link MessageDispatcher#dispatch} calls, and turns socket close events
 * into the spec's "auto-resign after 20 sec, with a countdown" behaviour.
 * No game/business logic lives here on purpose.
 */
public final class ChessWebSocketServer extends WebSocketServer {
    private final MessageDispatcher dispatcher;
    private final SessionRegistry sessionRegistry;
    private final RoomManager roomManager;
    private final MatchmakingService matchmakingService;
    private final ServerActivityLogger activityLogger;

    public ChessWebSocketServer(InetSocketAddress address, MessageDispatcher dispatcher,
                                 SessionRegistry sessionRegistry, RoomManager roomManager,
                                 MatchmakingService matchmakingService, ServerActivityLogger activityLogger) {
        super(address);
        this.dispatcher = dispatcher;
        this.sessionRegistry = sessionRegistry;
        this.roomManager = roomManager;
        this.matchmakingService = matchmakingService;
        this.activityLogger = activityLogger;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        sessionRegistry.getOrCreate(conn);
        System.out.println("[server] client connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        PlayerSession session = sessionRegistry.get(conn);
        sessionRegistry.remove(conn);
        if (session == null) return;

        System.out.println("[server] client disconnected: " + conn.getRemoteSocketAddress());

        if (session.getUser() != null) {
            matchmakingService.cancel(session.getUser().getId());
        }
        startDisconnectWatchdogIfNeeded(session);
    }

    @Override
    public void onMessage(WebSocket conn, String rawJson) {
        PlayerSession session = sessionRegistry.getOrCreate(conn);
        Message message = MessageCodec.decodeEnvelope(rawJson);
        Long userId = session.getUser() == null ? null : session.getUser().getId();
        activityLogger.logTraffic("IN", userId, message.getType().name(), rawJson);
        dispatcher.dispatch(session, message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[server] socket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("[server] listening on " + getAddress());
    }

    private void startDisconnectWatchdogIfNeeded(PlayerSession session) {
        User user = session.getUser();
        String roomId = session.getCurrentRoomId();
        if (user == null || roomId == null) return;

        Optional<Room> maybeRoom = roomManager.getRoom(roomId);
        if (maybeRoom.isEmpty()) return;

        Room room = maybeRoom.get();
        GameSession gameSession = room.getGameSession();
        if (gameSession == null || gameSession.isOver()) return;

        char color = gameSession.colorOf(user.getId());
        if (color != 'w' && color != 'b') return; // spectators don't trigger auto-resign

        room.detachMember(user.getId());
        DisconnectWatchdog watchdog = new DisconnectWatchdog(room, gameSession, user.getId(), color);
        session.setActiveWatchdog(watchdog);
        watchdog.start();
    }
}
