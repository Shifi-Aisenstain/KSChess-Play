package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class LoginPayload {
    public final String username;
    public final String password;
}
