package client.net;

import client.logging.ClientActivityLogger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;

import java.net.URI;

/**
 * Client-side counterpart of {@code server.net.ChessWebSocketServer}. Every
 * outgoing call goes through {@link #send}, every incoming frame is decoded
 * once here and handed to a single {@link ServerMessageListener} - callers
 * (mainly {@code client.bridge.NetworkGameController}) branch on
 * {@link Message#getType()} themselves rather than this class growing a
 * huge if/else chain of its own.
 */
public final class ServerConnection extends WebSocketClient {
    private final ServerMessageListener listener;
    private final ClientActivityLogger activityLogger;

    public ServerConnection(URI serverUri, ServerMessageListener listener, ClientActivityLogger activityLogger) {
        super(serverUri);
        this.listener = listener;
        this.activityLogger = activityLogger;
    }

    public void send(MessageType type, Object payload) {
        String json = MessageCodec.encode(type, payload);
        activityLogger.logTraffic("OUT", type.name(), json);
        send(json);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        activityLogger.logInfo("Connected to server.");
    }

    @Override
    public void onMessage(String rawJson) {
        Message message = MessageCodec.decodeEnvelope(rawJson);
        activityLogger.logTraffic("IN", message.getType().name(), rawJson);
        listener.onMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        activityLogger.logInfo("Disconnected from server: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        activityLogger.logInfo("Connection error: " + ex.getMessage());
    }
}
