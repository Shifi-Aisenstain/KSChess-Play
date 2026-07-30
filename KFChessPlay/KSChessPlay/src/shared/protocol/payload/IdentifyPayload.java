package shared.protocol.payload;

import lombok.AllArgsConstructor;

/**
 * Sent once, right after opening the WebSocket (PLAN_split_6_services.md §3.5): attaches an already
 * REST-authenticated identity (see {@code server.apigateway.ApiGatewayMain}'s {@code POST /login}) to
 * this connection's {@code PlayerSession}, replacing the old LOGIN(username, password) round trip
 * that used to authenticate directly over the socket.
 */
@AllArgsConstructor
public final class IdentifyPayload {
    public final long userId;
    public final String username;
    public final int elo;
}
