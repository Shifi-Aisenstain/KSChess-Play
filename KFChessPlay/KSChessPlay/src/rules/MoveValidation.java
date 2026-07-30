package rules;

public class MoveValidation {
    private final boolean isValid;
    private final String message;

    private MoveValidation(boolean isValid, String message) {
        this.isValid = isValid;
        this.message = message;
    }

    public boolean isValid() { return isValid; }
    public String getMessage() { return message; }

    public static MoveValidation valid() {
        return new MoveValidation(true, "");
    }

    public static MoveValidation invalid(String reason) {
        return new MoveValidation(false, reason);
    }

    @Override
    public String toString() {
        return isValid ? "✓ Valid" : "✗ Invalid: " + message;
    }
}
