package server.net;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import server.auth.User;
import server.dispatch.MessageDispatcher;
import server.gateway.DisconnectWatchdog;
import server.gateway.GatewayRoomRegistry;
import server.gateway.GatewayRoomSession;
import server.gateway.MatchmakingClient;
import server.logging.ServerActivityLogger;
import shared.protocol.Message;
import shared.protocol.MessageCodec;

import java.net.InetSocketAddress;

/**
 * Thin transport adapter: turns raw WebSocket callbacks into
 * {@link MessageDispatcher#dispatch} calls, and turns socket close events
 * into the spec's "auto-resign after 20 sec, with a countdown" behaviour.
 * No game/business logic lives here on purpose.
 *
 * <p>Since PLAN_split_6_services.md §3.2, this runs inside {@code server.gateway.WsGatewayMain}
 * rather than the monolith: disconnect handling now goes through {@link GatewayRoomRegistry}/
 * {@link GatewayRoomSession} (which relay straight to a room's Game Server Shard over the bus)
 * instead of a local {@code RoomManager}/{@code Room}, and matchmaking cancellation goes through
 * {@link MatchmakingClient} instead of an in-process {@code MatchmakingService}.
 */
public final class ChessWebSocketServer extends WebSocketServer {
    private final MessageDispatcher dispatcher;
    private final SessionRegistry sessionRegistry;
    private final GatewayRoomRegistry roomRegistry;
    private final MatchmakingClient matchmakingClient;
    private final ServerActivityLogger activityLogger;

    public ChessWebSocketServer(InetSocketAddress address, MessageDispatcher dispatcher,
                                 SessionRegistry sessionRegistry, GatewayRoomRegistry roomRegistry,
                                 MatchmakingClient matchmakingClient, ServerActivityLogger activityLogger) {
        super(address);
        this.dispatcher = dispatcher;
        this.sessionRegistry = sessionRegistry;
        this.roomRegistry = roomRegistry;
        this.matchmakingClient = matchmakingClient;
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
            matchmakingClient.cancel(session.getUser().getId());
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

        GatewayRoomSession roomSession = roomRegistry.get(roomId).orElse(null);
        if (roomSession == null || roomSession.isOver()) return;

        char color = roomSession.colorOf(user.getId());
        if (color != 'w' && color != 'b') return; // spectators don't trigger auto-resign

        roomSession.removeLocalMember(user.getId());
        DisconnectWatchdog watchdog = new DisconnectWatchdog(roomSession, user.getId(), color);
        roomSession.registerWatchdog(user.getId(), watchdog);
        watchdog.start();
    }
}
