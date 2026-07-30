package client.bridge;

import client.net.ServerConnection;
import client.net.ServerMessageListener;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.IdentifyPayload;
import shared.protocol.payload.LoginResultPayload;

import java.util.concurrent.CompletableFuture;

public final class LoginController implements ServerMessageListener {
    private final ServerConnection connection;
    private volatile CompletableFuture<LoginResultPayload> pending;

    public LoginController(ServerConnection connection) {
        this.connection = connection;
    }

    public CompletableFuture<LoginResultPayload> identify(LoginResultPayload authenticated) {
        CompletableFuture<LoginResultPayload> future = new CompletableFuture<>();
        this.pending = future;
        connection.send(MessageType.IDENTIFY, new IdentifyPayload(authenticated.sessionToken));
        return future;
    }

    @Override
    public void onMessage(Message message) {
        if (message.getType() != MessageType.LOGIN_RESULT) return;
        CompletableFuture<LoginResultPayload> future = pending;
        if (future != null) future.complete(MessageCodec.decodePayload(message, LoginResultPayload.class));
    }
}
