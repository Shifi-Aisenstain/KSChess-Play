package loadtest;

import client.logging.ClientActivityLogger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class LoadTestMain {
    private static final String DEFAULT_API_URL = "http://localhost:8080";
    private static final String DEFAULT_WS_URL = "ws://localhost:8887";
    private static final int DEFAULT_PLAYER_COUNT = 20;

    public static void main(String[] args) throws Exception {
        String apiUrl = argOrDefault(args, "--api-url", DEFAULT_API_URL);
        String wsUrl = argOrDefault(args, "--ws-url", DEFAULT_WS_URL);
        int playerCount = Integer.parseInt(argOrDefault(args, "--players", String.valueOf(DEFAULT_PLAYER_COUNT)));
        String csvPath = argOrNull(args, "--csv");
        if (playerCount % 2 != 0) {
            playerCount++;
            System.out.println("[loadtest] player count must be even for pairwise matchmaking, rounding up to " + playerCount);
        }

        String runId = String.valueOf(System.currentTimeMillis());
        System.out.println("[loadtest] run " + runId + ": " + playerCount + " simulated players against api=" + apiUrl + " ws=" + wsUrl);

        ClientActivityLogger activityLogger = new ClientActivityLogger("logs/loadtest/activity.log");
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(playerCount, 200));
        List<CompletableFuture<PlayerRunResult>> futures = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            int playerIndex = i;
            futures.add(CompletableFuture.supplyAsync(
                    () -> new SimulatedPlayer(playerIndex, apiUrl, wsUrl, runId, activityLogger).run(), executor));
        }

        List<PlayerRunResult> results = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        executor.shutdown();

        printReport(results);
        if (csvPath != null) writeCsv(csvPath, results);
    }

    private static void printReport(List<PlayerRunResult> results) {
        long loginOk = results.stream().filter(r -> r.loginSucceeded).count();
        long matchOk = results.stream().filter(r -> r.matchFound).count();
        long moveOk = results.stream().filter(r -> r.moveRoundTripCompleted).count();
        long gameOverOk = results.stream().filter(r -> r.gameEndedCleanly).count();

        LatencyStats loginStats = LatencyStats.of(results.stream()
                .filter(r -> r.loginSucceeded).map(r -> r.loginLatencyMs).collect(Collectors.toList()));
        LatencyStats matchStats = LatencyStats.of(results.stream()
                .filter(r -> r.matchFound).map(r -> r.matchmakingLatencyMs).collect(Collectors.toList()));
        LatencyStats moveStats = LatencyStats.of(results.stream()
                .filter(r -> r.moveRoundTripCompleted).map(r -> r.moveRoundTripMs).collect(Collectors.toList()));

        System.out.println();
        System.out.println("==================== KungFu Chess load test report ====================");
        System.out.println("players:            " + results.size());
        System.out.println("login succeeded:    " + loginOk + "/" + results.size());
        System.out.println("  latency:          " + loginStats);
        System.out.println("matched:            " + matchOk + "/" + results.size());
        System.out.println("  latency:          " + matchStats);
        System.out.println("move round trip ok: " + moveOk + "/" + results.size());
        System.out.println("  latency:          " + moveStats);
        System.out.println("game ended cleanly: " + gameOverOk + "/" + results.size());
        System.out.println();
        results.stream().filter(r -> r.failureReason != null).forEach(r ->
                System.out.println("  player " + r.playerIndex + " failed: " + r.failureReason));
        System.out.println("=========================================================================");
    }

    private static void writeCsv(String path, List<PlayerRunResult> results) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {
            writer.println("playerIndex,loginSucceeded,loginLatencyMs,matchFound,matchmakingLatencyMs,"
                    + "moveRoundTripCompleted,moveRoundTripMs,gameEndedCleanly,failureReason");
            for (PlayerRunResult r : results) {
                writer.println(r.playerIndex + "," + r.loginSucceeded + "," + r.loginLatencyMs + "," + r.matchFound + ","
                        + r.matchmakingLatencyMs + "," + r.moveRoundTripCompleted + "," + r.moveRoundTripMs + ","
                        + r.gameEndedCleanly + "," + (r.failureReason == null ? "" : r.failureReason.replace(",", ";")));
            }
        }
        System.out.println("[loadtest] wrote per-player results to " + path);
    }

    private static String argOrDefault(String[] args, String flag, String defaultValue) {
        String value = argOrNull(args, flag);
        return value != null ? value : defaultValue;
    }

    private static String argOrNull(String[] args, String flag) {
        String prefix = flag + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) return arg.substring(prefix.length());
        }
        return null;
    }
}
