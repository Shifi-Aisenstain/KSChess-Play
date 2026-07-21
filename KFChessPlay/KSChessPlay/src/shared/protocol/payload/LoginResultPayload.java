package shared.protocol.payload;

public final class LoginResultPayload {
    public final boolean success;
    public final long userId;
    public final String username;
    public final int elo;
    public final String errorMessage;

    private LoginResultPayload(boolean success, long userId, String username, int elo, String errorMessage) {
        this.success = success;
        this.userId = userId;
        this.username = username;
        this.elo = elo;
        this.errorMessage = errorMessage;
    }

    public static LoginResultPayload ok(long userId, String username, int elo) {
        return new LoginResultPayload(true, userId, username, elo, null);
    }

    public static LoginResultPayload fail(String reason) {
        return new LoginResultPayload(false, -1, null, 0, reason);
    }
}
