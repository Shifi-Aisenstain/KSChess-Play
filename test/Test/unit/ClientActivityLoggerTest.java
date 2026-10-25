package Test.unit;

import client.logging.ClientActivityLogger;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ClientActivityLoggerTest {

    private String newLogPath() throws Exception {
        return Files.createTempFile("kfchess-test-clientactivity-", ".log").toString();
    }

    @Test
    public void testLogTrafficWritesDirectionTypeAndPayload() throws Exception {
        String path = newLogPath();
        ClientActivityLogger logger = new ClientActivityLogger(path);

        logger.logTraffic("OUT", "MOVE", "{\"fromRow\":6}");

        String content = Files.readString(Path.of(path));
        assertTrue(content.contains("OUT type=MOVE payload={\"fromRow\":6}"));
    }

    @Test
    public void testLogInfoWritesInfoPrefixedLine() throws Exception {
        String path = newLogPath();
        ClientActivityLogger logger = new ClientActivityLogger(path);

        logger.logInfo("Connected to server.");

        String content = Files.readString(Path.of(path));
        assertTrue(content.contains("INFO Connected to server."));
    }

    @Test
    public void testAppendsAcrossMultipleLoggerInstances() throws Exception {
        String path = newLogPath();
        new ClientActivityLogger(path).logInfo("first line");
        new ClientActivityLogger(path).logInfo("second line");

        String content = Files.readString(Path.of(path));
        assertTrue(content.contains("first line"));
        assertTrue(content.contains("second line"));
    }
}
