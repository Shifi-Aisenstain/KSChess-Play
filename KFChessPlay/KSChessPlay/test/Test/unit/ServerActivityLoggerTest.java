package Test.unit;

import models.Piece;
import models.Position;
import org.junit.jupiter.api.Test;
import server.logging.ServerActivityLogger;
import shared.eventbus.EventBus;
import shared.eventbus.events.GameEndedEvent;
import shared.eventbus.events.GameStartedEvent;
import shared.eventbus.events.MoveExecutedEvent;
import shared.eventbus.events.PieceJumpedEvent;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ServerActivityLoggerTest {

    private String newLogPath() throws Exception {
        return Files.createTempFile("kfchess-test-serveractivity-", ".log").toString();
    }

    private String readLog(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void testGameStartedEventIsLogged() throws Exception {
        String path = newLogPath();
        EventBus eventBus = new EventBus();
        new ServerActivityLogger(path, eventBus);

        eventBus.publish(new GameStartedEvent("ROOM1", "alice", "bob"));

        String content = readLog(path);
        assertTrue(content.contains("GAME_STARTED"));
        assertTrue(content.contains("alice"));
    }

    @Test
    public void testGameEndedEventIsLogged() throws Exception {
        String path = newLogPath();
        EventBus eventBus = new EventBus();
        new ServerActivityLogger(path, eventBus);

        eventBus.publish(new GameEndedEvent("ROOM1", "WHITE", "checkmate", "alice", "bob"));

        assertTrue(readLog(path).contains("GAME_ENDED"));
    }

    @Test
    public void testMoveExecutedEventIsLogged() throws Exception {
        String path = newLogPath();
        EventBus eventBus = new EventBus();
        new ServerActivityLogger(path, eventBus);

        eventBus.publish(new MoveExecutedEvent("ROOM1", new Piece('w', 'P'), new Position(6, 4),
                new Position(4, 4), null, "Pe4", 0, 0, "00:00.000|Pe4"));

        assertTrue(readLog(path).contains("MOVE Pe4"));
    }

    @Test
    public void testPieceJumpedEventIsLogged() throws Exception {
        String path = newLogPath();
        EventBus eventBus = new EventBus();
        new ServerActivityLogger(path, eventBus);

        eventBus.publish(new PieceJumpedEvent("ROOM1", new Piece('w', 'K'), new Position(7, 3)));

        assertTrue(readLog(path).contains("JUMP"));
    }

    @Test
    public void testLogTrafficWritesDirectionTypeAndUser() throws Exception {
        String path = newLogPath();
        ServerActivityLogger logger = new ServerActivityLogger(path, new EventBus());

        logger.logTraffic("IN", 42L, "LOGIN", "{\"username\":\"alice\"}");

        String content = readLog(path);
        assertTrue(content.contains("IN user=42 type=LOGIN"));
    }

    @Test
    public void testLogTrafficWithNullUserIdSaysAnonymous() throws Exception {
        String path = newLogPath();
        ServerActivityLogger logger = new ServerActivityLogger(path, new EventBus());

        logger.logTraffic("IN", null, "PING", "{}");

        assertTrue(readLog(path).contains("user=anonymous"));
    }
}
