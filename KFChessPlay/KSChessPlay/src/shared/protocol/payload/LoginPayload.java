package shared.protocol.payload;

public final class LoginPayload {
    public final String username;
    public final String password;

    public LoginPayload(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
