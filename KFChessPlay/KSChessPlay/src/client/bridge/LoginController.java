package client.bridge;

import client.net.ServerConnection;
import client.net.ServerMessageListener;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.LoginPayload;
import shared.protocol.payload.LoginResultPayload;

import java.util.concurrent.CompletableFuture;

/**
 * Owns exactly one thing: the LOGIN / LOGIN_RESULT round-trip. {@code Main}
 * uses this while the user is still typing credentials, then hands the
 * connection off to {@link NetworkGameController} for the rest of the
 * session - this class is never involved once login succeeds.
 */
public final class LoginController implements ServerMessageListener {
    private final ServerConnection connection;
    private volatile CompletableFuture<LoginResultPayload> pending;

    public LoginController(ServerConnection connection) {
        this.connection = connection;
    }

    public CompletableFuture<LoginResultPayload> login(String username, String password) {
        CompletableFuture<LoginResultPayload> future = new CompletableFuture<>();
        this.pending = future;
        connection.send(MessageType.LOGIN, new LoginPayload(username, password));
        return future;
    }

    @Override
    public void onMessage(Message message) {
        if (message.getType() != MessageType.LOGIN_RESULT) return;
        CompletableFuture<LoginResultPayload> future = pending;
        if (future != null) future.complete(MessageCodec.decodePayload(message, LoginResultPayload.class));
    }
}
