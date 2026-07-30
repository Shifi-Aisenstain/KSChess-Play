package server.apigateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.auth.AuthService;
import shared.protocol.MessageCodec;
import shared.protocol.payload.LoginPayload;
import shared.protocol.payload.LoginResultPayload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles {@code POST /login} (PLAN_split_6_services.md §3.5) - the one REST endpoint in this whole
 * project; see {@link ApiGatewayMain}'s javadoc for why login specifically is REST and everything
 * else stays on the WS protocol/NATS. Reuses the existing {@link LoginPayload}/{@link LoginResultPayload}
 * Gson DTOs as the HTTP request/response bodies via {@link MessageCodec}'s envelope-free
 * {@code encode}/{@code decodeInnerPayload} - no new (de)serialization code needed, and the Swing
 * client decodes the response with the exact same class it used for the old {@code LOGIN_RESULT}
 * socket message.
 *
 * <p>Always replies 200 with a JSON body, {@code success: true/false} inside it, even for wrong
 * credentials - matching this protocol's existing style everywhere else (the WS side never used
 * HTTP-style status codes for business-logic failures either), and avoiding leaking anything through
 * the status code itself. A malformed/non-POST request is the one case that gets a real HTTP error
 * status, since that's a client bug, not a login outcome.
 *
 * <p>Unit tested against a real loopback {@code HttpServer} (see {@code Test.unit.LoginRestHandlerTest})
 * rather than a hand-built {@code HttpExchange} fake ({@code HttpExchange} is an abstract class with
 * many members, not an interface a {@code java.lang.reflect.Proxy} can stand in for) - the same way
 * {@code TestSupport.LoopbackServer} tests the WS side against a real socket instead of mocking the
 * (final) WebSocket client class.
 */
public final class LoginRestHandler implements HttpHandler {
    private final AuthService authService;

    public LoginRestHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        LoginPayload request;
        try (InputStream body = exchange.getRequestBody()) {
            String json = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            request = MessageCodec.decodeInnerPayload(json, LoginPayload.class);
        } catch (RuntimeException e) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }

        AuthService.Result result = authService.login(request.username, request.password);
        LoginResultPayload response = result.success
                ? LoginResultPayload.ok(result.user.getId(), result.user.getUsername(), result.user.getElo())
                : LoginResultPayload.fail(result.errorMessage);

        byte[] responseBytes = MessageCodec.encodeInnerPayload(response).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(responseBytes);
        }
    }
}
