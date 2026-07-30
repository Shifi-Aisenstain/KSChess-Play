package client.net;

import shared.protocol.MessageCodec;
import shared.protocol.payload.LoginPayload;
import shared.protocol.payload.LoginResultPayload;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client-side counterpart of {@code server.apigateway.ApiGatewayMain} (PLAN_split_6_services.md
 * §3.5): a plain {@link HttpClient} (built into the JDK, same "no new dependency" reasoning as the
 * server side) that calls {@code POST /login} and gets back the same {@link LoginResultPayload}
 * shape the old LOGIN_RESULT socket message used to carry - {@code client.bridge.LoginController}
 * uses this before opening the WebSocket at all now, instead of sending LOGIN over the socket.
 */
public final class ApiGatewayClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final URI loginUri;

    public ApiGatewayClient(String apiBaseUrl) {
        this.loginUri = URI.create(apiBaseUrl + "/login");
    }

    /** @throws IOException if the API Gateway can't be reached at all (connection refused, DNS, ...) */
    public LoginResultPayload login(String username, String password) throws IOException, InterruptedException {
        String requestBody = MessageCodec.encodeInnerPayload(new LoginPayload(username, password));
        HttpRequest request = HttpRequest.newBuilder(loginUri)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return LoginResultPayload.fail("API Gateway returned HTTP " + response.statusCode());
        }
        return MessageCodec.decodeInnerPayload(response.body(), LoginResultPayload.class);
    }
}
