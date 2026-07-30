package server.apigateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.history.GameHistoryRepository;
import server.history.GameSummary;
import server.history.MoveEntry;
import server.logging.ServerActivityLogger;
import shared.metrics.MetricsRegistry;
import shared.protocol.MessageCodec;
import shared.tracing.JaegerReporter;
import shared.tracing.Span;
import shared.tracing.TraceContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HistoryRestHandler implements HttpHandler {
    private static final String SERVICE_NAME = "apigateway";

    private final GameHistoryRepository historyRepository;
    private final ServerActivityLogger activityLogger;
    private final MetricsRegistry metricsRegistry;
    private final JaegerReporter jaegerReporter;

    public HistoryRestHandler(GameHistoryRepository historyRepository, ServerActivityLogger activityLogger,
                               MetricsRegistry metricsRegistry, JaegerReporter jaegerReporter) {
        this.historyRepository = historyRepository;
        this.activityLogger = activityLogger;
        this.metricsRegistry = metricsRegistry;
        this.jaegerReporter = jaegerReporter;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String path = exchange.getRequestURI().getPath();
        boolean isMoves = path.endsWith("/history/moves");
        String traceId = TraceContext.newId();
        TraceContext.set(traceId);
        Span span = Span.start(traceId, isMoves ? "apigateway.history.moves" : "apigateway.history.games");
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            if (isMoves) {
                handleMoves(exchange, query);
            } else {
                handleGames(exchange, query);
            }
        } finally {
            jaegerReporter.report(span, SERVICE_NAME);
            TraceContext.clear();
        }
    }

    private void handleGames(HttpExchange exchange, Map<String, String> query) throws IOException {
        String userIdParam = query.get("userId");
        if (userIdParam == null) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }

        long userId;
        try {
            userId = Long.parseLong(userIdParam);
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }

        List<GameSummary> games = historyRepository.findGamesByUserId(userId);
        activityLogger.logTraffic("IN", userId, "HISTORY_GAMES_REQUEST", "userId=" + userId);
        activityLogger.logTraffic("OUT", userId, "HISTORY_GAMES_RESULT", "count=" + games.size());
        metricsRegistry.incrementCounter("apigateway_history_games_requests_total", "Total GET /history requests");
        writeJson(exchange, games);
    }

    private void handleMoves(HttpExchange exchange, Map<String, String> query) throws IOException {
        String roomId = query.get("roomId");
        if (roomId == null || roomId.isEmpty()) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }

        List<MoveEntry> moves = historyRepository.findMovesByRoomId(roomId);
        activityLogger.logTraffic("IN", null, "HISTORY_MOVES_REQUEST", "roomId=" + roomId);
        activityLogger.logTraffic("OUT", null, "HISTORY_MOVES_RESULT", "roomId=" + roomId + " count=" + moves.size());
        metricsRegistry.incrementCounter("apigateway_history_moves_requests_total", "Total GET /history/moves requests");
        writeJson(exchange, moves);
    }

    private void writeJson(HttpExchange exchange, Object body) throws IOException {
        byte[] responseBytes = MessageCodec.encodeInnerPayload(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(responseBytes);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, equalsIndex), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(equalsIndex + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }
}
