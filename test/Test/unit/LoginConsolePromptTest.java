package Test.unit;

import client.ui.LoginConsolePrompt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class LoginConsolePromptTest {

    private InputStream originalIn;

    @BeforeEach
    public void saveOriginalStdin() {
        originalIn = System.in;
    }

    @AfterEach
    public void restoreOriginalStdin() {
        System.setIn(originalIn);
    }

    @Test
    public void testPromptReadsUsernameAndPasswordLines() {
        System.setIn(new ByteArrayInputStream("alice\nhunter2\n".getBytes(StandardCharsets.UTF_8)));

        LoginConsolePrompt.Credentials creds = new LoginConsolePrompt().prompt();

        assertEquals("alice", creds.username);
        assertEquals("hunter2", creds.password);
    }

    @Test
    public void testPromptTrimsWhitespace() {
        System.setIn(new ByteArrayInputStream("  bob  \n  secret  \n".getBytes(StandardCharsets.UTF_8)));

        LoginConsolePrompt.Credentials creds = new LoginConsolePrompt().prompt();

        assertEquals("bob", creds.username);
        assertEquals("secret", creds.password);
    }
}
