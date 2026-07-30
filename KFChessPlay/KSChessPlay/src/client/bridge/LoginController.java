package client.bridge;

import client.net.ServerConnection;
import client.net.ServerMessageListener;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.IdentifyPayload;
import shared.protocol.payload.LoginResultPayload;

import java.util.concurrent.CompletableFuture;

/**
 * Owns exactly one thing: the IDENTIFY / LOGIN_RESULT round-trip. Since PLAN_split_6_services.md
 * §3.5, authentication itself already happened over REST (see {@code client.net.ApiGatewayClient})
 * before the socket was even opened - {@code Main} calls {@link #identify} with that already-proven
 * identity to attach it to this connection, then hands the connection off to
 * {@link NetworkGameController} for the rest of the session, exactly as before.
 */
public final class LoginController implements ServerMessageListener {
    private final ServerConnection connection;
    private volatile CompletableFuture<LoginResultPayload> pending;

    public LoginController(ServerConnection connection) {
        this.connection = connection;
    }

    /** @param authenticated the result of an already-successful {@code ApiGatewayClient.login} call */
    public CompletableFuture<LoginResultPayload> identify(LoginResultPayload authenticated) {
        CompletableFuture<LoginResultPayload> future = new CompletableFuture<>();
        this.pending = future;
        connection.send(MessageType.IDENTIFY,
                new IdentifyPayload(authenticated.userId, authenticated.username, authenticated.elo));
        return future;
    }

    @Override
    public void onMessage(Message message) {
        if (message.getType() != MessageType.LOGIN_RESULT) return;
        CompletableFuture<LoginResultPayload> future = pending;
        if (future != null) future.complete(MessageCodec.decodePayload(message, LoginResultPayload.class));
    }
}
