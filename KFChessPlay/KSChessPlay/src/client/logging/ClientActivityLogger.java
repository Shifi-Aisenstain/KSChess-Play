package client.logging;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Client-side half of "store logs on both server and client side, for all of the client/server activity." */
public final class ClientActivityLogger {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final PrintWriter writer;

    public ClientActivityLogger(String logFilePath) {
        try {
            this.writer = new PrintWriter(new FileWriter(logFilePath, true), true);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open client log file: " + logFilePath, e);
        }
    }

    public synchronized void logTraffic(String direction, String messageType, String json) {
        writer.println("[" + LocalDateTime.now().format(TIMESTAMP) + "] " + direction + " type=" + messageType + " payload=" + json);
    }

    public synchronized void logInfo(String line) {
        writer.println("[" + LocalDateTime.now().format(TIMESTAMP) + "] INFO " + line);
    }
}
