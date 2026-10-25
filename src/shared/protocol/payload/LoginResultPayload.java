package shared.protocol.payload;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class LoginResultPayload {
    public final boolean success;
    public final long userId;
    public final String username;
    public final int elo;
    public final String errorMessage;
    public final String sessionToken;

    public static LoginResultPayload ok(long userId, String username, int elo, String sessionToken) {
        return new LoginResultPayload(true, userId, username, elo, null, sessionToken);
    }

    public static LoginResultPayload fail(String reason) {
        return new LoginResultPayload(false, -1, null, 0, reason, null);
    }
}
