package Test.unit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.apigateway.HistoryRestHandler;
import server.history.GameSummary;
import server.history.MoveEntry;
import server.persistence.SchemaInitializer;
import server.persistence.SqliteConnectionProvider;
import server.persistence.SqliteGameHistoryRepository;
import shared.metrics.MetricsRegistry;
import shared.protocol.MessageCodec;
import shared.tracing.JaegerReporter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class HistoryRestHandlerTest {

    private HttpServer server;
    private SqliteGameHistoryRepository repository;
    private HttpClient client;
    private String baseUrl;
    private MetricsRegistry metricsRegistry;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) throws IOException {
        SqliteConnectionProvider connectionProvider = new SqliteConnectionProvider(tempDir.resolve("history-rest-test.db").toString());
        new SchemaInitializer(connectionProvider).initialize();
        repository = new SqliteGameHistoryRepository(connectionProvider);

        metricsRegistry = new MetricsRegistry();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/history", new HistoryRestHandler(repository, TestSupport.newActivityLoggerOrThrow(), metricsRegistry,
                new JaegerReporter("http://localhost:1/not-a-real-jaeger")));
        server.setExecutor(null);
        server.start();
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(5)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void testGamesEndpointReturnsGamesForUser() throws Exception {
        repository.saveGame("ROOM1", 1L, 2L, "alice", "bob", "WHITE", "checkmate", 16, -16, 1000L);

        HttpResponse<String> response = get("/history?userId=1");

        assertEquals(200, response.statusCode());
        GameSummary[] games = MessageCodec.decodeInnerPayload(response.body(), GameSummary[].class);
        assertEquals(1, games.length);
        assertEquals("ROOM1", games[0].roomId);
        assertEquals("alice", games[0].whiteUsername);
    }

    @Test
    public void testGamesEndpointReturnsEmptyArrayForUnknownUser() throws Exception {
        HttpResponse<String> response = get("/history?userId=999");

        assertEquals(200, response.statusCode());
        GameSummary[] games = MessageCodec.decodeInnerPayload(response.body(), GameSummary[].class);
        assertEquals(0, games.length);
    }

    @Test
    public void testGamesEndpointMissingUserIdReturns400() throws Exception {
        HttpResponse<String> response = get("/history");

        assertEquals(400, response.statusCode());
    }

    @Test
    public void testGamesEndpointNonNumericUserIdReturns400() throws Exception {
        HttpResponse<String> response = get("/history?userId=notanumber");

        assertEquals(400, response.statusCode());
    }

    @Test
    public void testMovesEndpointReturnsMovesForRoomInOrder() throws Exception {
        repository.saveMove("ROOM1", "e4", 1L);
        repository.saveMove("ROOM1", "e5", 2L);

        HttpResponse<String> response = get("/history/moves?roomId=ROOM1");

        assertEquals(200, response.statusCode());
        MoveEntry[] moves = MessageCodec.decodeInnerPayload(response.body(), MoveEntry[].class);
        assertEquals(2, moves.length);
        assertEquals("e4", moves[0].notation);
        assertEquals("e5", moves[1].notation);
    }

    @Test
    public void testMovesEndpointMissingRoomIdReturns400() throws Exception {
        HttpResponse<String> response = get("/history/moves");

        assertEquals(400, response.statusCode());
    }

    @Test
    public void testNonGetMethodRejected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/history?userId=1"))
                .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
    }

    @Test
    public void testMetricsCountGamesAndMovesRequestsSeparately() throws Exception {
        get("/history?userId=1");
        get("/history/moves?roomId=ROOM1");
        get("/history/moves?roomId=ROOM1");

        String rendered = metricsRegistry.renderPrometheusText();
        assertTrue(rendered.contains("apigateway_history_games_requests_total 1"));
        assertTrue(rendered.contains("apigateway_history_moves_requests_total 2"));
    }
}
