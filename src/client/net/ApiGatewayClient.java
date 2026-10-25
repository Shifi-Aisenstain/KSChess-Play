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

public final class ApiGatewayClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final URI loginUri;

    public ApiGatewayClient(String apiBaseUrl) {
        this.loginUri = URI.create(apiBaseUrl + "/login");
    }

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
