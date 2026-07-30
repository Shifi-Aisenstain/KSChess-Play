package server.logging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import shared.eventbus.EventBus;
import shared.eventbus.events.GameEndedEvent;
import shared.eventbus.events.GameStartedEvent;
import shared.eventbus.events.MoveExecutedEvent;
import shared.eventbus.events.PieceJumpedEvent;
import shared.tracing.TraceContext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ServerActivityLogger {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Gson STDOUT_JSON = new GsonBuilder().create();

    private final PrintWriter writer;

    public ServerActivityLogger(String logFilePath, EventBus eventBus) {
        try {
            File logFile = new File(logFilePath);
            File parentDir = logFile.getParentFile();
            if (parentDir != null) parentDir.mkdirs();
            this.writer = new PrintWriter(new FileWriter(logFile, true), true);
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

    public void logFatal(String context, String message) {
        writeLine("FATAL", "FATAL [" + context + "] " + message);
    }

    private void writeLine(String line) {
        writeLine("INFO", line);
    }

    private synchronized void writeLine(String level, String line) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP);
        String traceId = TraceContext.current();
        String traceTag = traceId == null ? "" : " [trace=" + traceId + "]";
        writer.println("[" + timestamp + "]" + traceTag + " " + line);
        System.out.println(STDOUT_JSON.toJson(new StructuredLogLine(timestamp, level, line, traceId)));
    }

    private record StructuredLogLine(String timestamp, String level, String message, String traceId) { }
}
