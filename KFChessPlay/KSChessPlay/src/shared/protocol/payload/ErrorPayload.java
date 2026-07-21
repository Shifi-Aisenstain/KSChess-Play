package shared.protocol.payload;

public final class ErrorPayload {
    public final String message;

    public ErrorPayload(String message) {
        this.message = message;
    }
}
