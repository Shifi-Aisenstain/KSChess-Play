package server.apigateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.auth.AuthService;
import server.auth.SessionStore;
import server.logging.ServerActivityLogger;
import shared.metrics.MetricsRegistry;
import shared.protocol.MessageCodec;
import shared.protocol.payload.LoginPayload;
import shared.protocol.payload.LoginResultPayload;
import shared.tracing.JaegerReporter;
import shared.tracing.Span;
import shared.tracing.TraceContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class LoginRestHandler implements HttpHandler {
    private static final String SERVICE_NAME = "apigateway";

    private final AuthService authService;
    private final ServerActivityLogger activityLogger;
    private final MetricsRegistry metricsRegistry;
    private final SessionStore sessionStore;
    private final JaegerReporter jaegerReporter;

    public LoginRestHandler(AuthService authService, ServerActivityLogger activityLogger, MetricsRegistry metricsRegistry,
                             SessionStore sessionStore, JaegerReporter jaegerReporter) {
        this.authService = authService;
        this.activityLogger = activityLogger;
        this.metricsRegistry = metricsRegistry;
        this.sessionStore = sessionStore;
        this.jaegerReporter = jaegerReporter;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String traceId = TraceContext.newId();
        TraceContext.set(traceId);
        Span span = Span.start(traceId, "apigateway.login");
        try {
            doHandle(exchange);
        } finally {
            jaegerReporter.report(span, SERVICE_NAME);
            TraceContext.clear();
        }
    }

    private void doHandle(HttpExchange exchange) throws IOException {
        LoginPayload request;
        try (InputStream body = exchange.getRequestBody()) {
            String json = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            request = MessageCodec.decodeInnerPayload(json, LoginPayload.class);
        } catch (RuntimeException e) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }

        activityLogger.logTraffic("IN", null, "LOGIN", "username=" + request.username);
        metricsRegistry.incrementCounter("apigateway_login_attempts_total", "Total login attempts received");
        AuthService.Result result = authService.login(request.username, request.password);
        LoginResultPayload response = result.success
                ? LoginResultPayload.ok(result.user.getId(), result.user.getUsername(), result.user.getElo(),
                        sessionStore.createSession(result.user.getId(), result.user.getUsername(), result.user.getElo()))
                : LoginResultPayload.fail(result.errorMessage);
        activityLogger.logTraffic("OUT", result.success ? result.user.getId() : null, "LOGIN_RESULT",
                result.success ? "success username=" + result.user.getUsername() : "failure: " + result.errorMessage);
        metricsRegistry.incrementCounter(result.success ? "apigateway_login_success_total" : "apigateway_login_failure_total",
                "Total logins that " + (result.success ? "succeeded" : "failed"));

        byte[] responseBytes = MessageCodec.encodeInnerPayload(response).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(responseBytes);
        }
    }
}
