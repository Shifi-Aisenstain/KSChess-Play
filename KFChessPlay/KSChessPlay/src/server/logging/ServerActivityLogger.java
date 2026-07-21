package server.logging;

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
 */
public final class ServerActivityLogger {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

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

    private synchronized void writeLine(String line) {
        writer.println("[" + LocalDateTime.now().format(TIMESTAMP) + "] " + line);
    }
}
