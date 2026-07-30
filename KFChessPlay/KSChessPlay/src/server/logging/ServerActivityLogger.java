package server.logging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import shared.eventbus.EventBus;
import shared.eventbus.events.GameEndedEvent;
import shared.eventbus.events.GameStartedEvent;
import shared.eventbus.events.MoveExecutedEvent;
import shared.eventbus.events.PieceJumpedEvent;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Two responsibilities, one log file:
 * <ol>
 *   <li>Subscribes to the domain {@link EventBus} (this is the "Update Move
 *       Logs" subscriber the course spec's pub/sub-bus requirement asks
 *       for) and writes a human-readable play-by-play.</li>
 *   <li>Exposes {@link #logTraffic} for the raw wire-level record of every
 *       message in and out, satisfying "store logs on both server and
 *       client side, for all of the client/server activity".</li>
 * </ol>
 *
 * <p>Since PLAN_split_6_services.md §3.6, every line written here is <em>also</em> emitted as one
 * structured JSON object per line on stdout (alongside the existing human-readable file line, not
 * instead of it) - convenient for a future log-aggregation setup (e.g. a container log driver
 * shipping stdout to a central store) to parse without scraping the file format. Real dashboards/
 * metrics (Prometheus etc.) stay explicitly out of scope for this sub-step, per the plan's own framing.
 */
public final class ServerActivityLogger {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Gson STDOUT_JSON = new GsonBuilder().create();

    private final PrintWriter writer;

    public ServerActivityLogger(String logFilePath, EventBus eventBus) {
        try {
            this.writer = new PrintWriter(new FileWriter(logFilePath, true), true);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open server log file: " + logFilePath, e);
        }
        subscribeToDomainEvents(eventBus);
    }

    private void subscribeToDomainEvents(EventBus eventBus) {
        eventBus.subscribe(GameStartedEvent.class, e ->
                writeLine("[room " + e.roomId + "] GAME_STARTED white=" + e.whiteUsername + " black=" + e.blackUsername));

        eventBus.subscribe(GameEndedEvent.class, e ->
                writeLine("[room " + e.roomId + "] GAME_ENDED winner=" + e.winnerColor + " reason=" + e.reason));

        eventBus.subscribe(MoveExecutedEvent.class, e ->
                writeLine("[room " + e.roomId + "] MOVE " + e.notation
                        + " scoreWhite=" + e.scoreWhite + " scoreBlack=" + e.scoreBlack));

        eventBus.subscribe(PieceJumpedEvent.class, e ->
                writeLine("[room " + e.roomId + "] JUMP " + e.piece.getColor() + e.piece.getType()
                        + "@(" + e.at.getRow() + "," + e.at.getCol() + ")"));
    }

    public void logTraffic(String direction, Long userId, String messageType, String json) {
        writeLine(direction + " user=" + (userId == null ? "anonymous" : userId) + " type=" + messageType + " payload=" + json);
    }

    /** For unrecoverable startup/infrastructure failures (e.g. the database can't be opened) that
     *  aren't wire traffic but still need a clear, timestamped trail in the activity log. */
    public void logFatal(String context, String message) {
        writeLine("FATAL", "FATAL [" + context + "] " + message);
    }

    private void writeLine(String line) {
        writeLine("INFO", line);
    }

    private synchronized void writeLine(String level, String line) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP);
        writer.println("[" + timestamp + "] " + line);
        System.out.println(STDOUT_JSON.toJson(new StructuredLogLine(timestamp, level, line)));
    }

    /** One JSON object per stdout line (PLAN_split_6_services.md §3.6) - a plain data carrier, not a
     *  wire protocol type, so it deliberately doesn't go through {@code shared.protocol.MessageCodec}. */
    private record StructuredLogLine(String timestamp, String level, String message) { }
}
