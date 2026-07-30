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
import shared.metrics.MetricsRegistry;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.tracing.JaegerReporter;
import shared.tracing.Span;
import shared.tracing.TraceContext;

import java.net.InetSocketAddress;

public final class ChessWebSocketServer extends WebSocketServer {
    private static final String SERVICE_NAME = "gateway";

    private final MessageDispatcher dispatcher;
    private final SessionRegistry sessionRegistry;
    private final GatewayRoomRegistry roomRegistry;
    private final MatchmakingClient matchmakingClient;
    private final ServerActivityLogger activityLogger;
    private final MetricsRegistry metricsRegistry;
    private final JaegerReporter jaegerReporter;

    public ChessWebSocketServer(InetSocketAddress address, MessageDispatcher dispatcher,
                                 SessionRegistry sessionRegistry, GatewayRoomRegistry roomRegistry,
                                 MatchmakingClient matchmakingClient, ServerActivityLogger activityLogger,
                                 MetricsRegistry metricsRegistry, JaegerReporter jaegerReporter) {
        super(address);
        this.dispatcher = dispatcher;
        this.sessionRegistry = sessionRegistry;
        this.roomRegistry = roomRegistry;
        this.matchmakingClient = matchmakingClient;
        this.activityLogger = activityLogger;
        this.metricsRegistry = metricsRegistry;
        this.jaegerReporter = jaegerReporter;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        sessionRegistry.getOrCreate(conn);
        metricsRegistry.incrementCounter("gateway_connections_total", "Total WebSocket connections accepted");
        metricsRegistry.incrementGauge("gateway_active_connections", "Currently open WebSocket connections");
        System.out.println("[server] client connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        PlayerSession session = sessionRegistry.get(conn);
        sessionRegistry.remove(conn);
        metricsRegistry.decrementGauge("gateway_active_connections", "Currently open WebSocket connections");
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
        TraceContext.runWithTrace(message.getTraceId(), () -> {
            activityLogger.logTraffic("IN", userId, message.getType().name(), rawJson);
            Span span = Span.start(TraceContext.current(), "gateway." + message.getType().name())
                    .tag("message.type", message.getType().name());
            if (userId != null) span.tag("user.id", String.valueOf(userId));
            try {
                dispatcher.dispatch(session, message);
            } finally {
                jaegerReporter.report(span, SERVICE_NAME);
            }
        });
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
        if (color != 'w' && color != 'b') return;

        roomSession.removeLocalMember(user.getId());
        DisconnectWatchdog watchdog = new DisconnectWatchdog(roomSession, user.getId(), color);
        roomSession.registerWatchdog(user.getId(), watchdog);
        watchdog.start();
    }
}
