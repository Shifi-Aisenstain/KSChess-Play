package shared.tracing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JaegerReporter {
    private static final Gson GSON = new GsonBuilder().create();

    private final String zipkinEndpoint;
    private final HttpClient httpClient;

    public JaegerReporter(String zipkinEndpoint) {
        this.zipkinEndpoint = zipkinEndpoint;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void report(Span span, String serviceName) {
        span.finish();
        String json = toZipkinJson(span, serviceName);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(zipkinEndpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        System.err.println("[tracing] failed to report span to " + zipkinEndpoint + ": " + ex.getMessage());
                        return null;
                    });
        } catch (RuntimeException e) {
            System.err.println("[tracing] failed to build span request for " + zipkinEndpoint + ": " + e.getMessage());
        }
    }

    public static String toZipkinJson(Span span, String serviceName) {
        Map<String, Object> spanMap = new LinkedHashMap<>();
        spanMap.put("traceId", span.traceId);
        spanMap.put("id", span.spanId);
        spanMap.put("name", span.name);
        spanMap.put("timestamp", span.startEpochMicros);
        spanMap.put("duration", Math.max(1, span.durationMicros()));
        spanMap.put("localEndpoint", Map.of("serviceName", serviceName));
        if (!span.tags().isEmpty()) {
            spanMap.put("tags", span.tags());
        }
        return GSON.toJson(List.of(spanMap));
    }
}
