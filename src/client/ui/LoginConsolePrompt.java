package client.ui;

import java.util.Scanner;

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
