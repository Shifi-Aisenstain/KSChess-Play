package client.ui;

import java.util.Scanner;

/**
 * Spec: "Login with username (just for presentation) ... Do it in a shell,
 * not via GUI" (later extended to "username + password, save at SQLite db
 * on server side"). This class owns only the console I/O; the network
 * round-trip to actually authenticate lives in {@code Main}, so this class
 * has nothing to do with sockets and is trivially testable.
 */
public final class LoginConsolePrompt {

    public static final class Credentials {
        public final String username;
        public final String password;

        public Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    public Credentials prompt() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== KungFu Chess - Login ===");
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine().trim();
        return new Credentials(username, password);
    }
}
