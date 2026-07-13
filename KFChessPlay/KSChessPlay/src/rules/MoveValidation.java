package rules;

/**
 * Immutable result object representing move validation status.
 * Follows the Result Pattern to communicate validation failures with detailed messages.
 * 
 * ✅ Design Benefits:
 * - Caller knows WHY validation failed (not just "false")
 * - Easy to test and debug
 * - Decoupled from exceptions
 */
public class MoveValidation {
    private final boolean isValid;
    private final String message; // מה הבעיה? (לדיבאג וטסטים)
    
    private MoveValidation(boolean isValid, String message) {
        this.isValid = isValid;
        this.message = message;
    }
    
    // ✅ Getters
    public boolean isValid() { return isValid; }
    public String getMessage() { return message; }
    
    // ✅ Factory methods (Builder Pattern) - קלות שימוש
    public static MoveValidation valid() {
        return new MoveValidation(true, "");
    }
    
    public static MoveValidation invalid(String reason) {
        return new MoveValidation(false, reason);
    }
    
    // ✅ Debug friendly
    @Override
    public String toString() {
        return isValid ? "✓ Valid" : "✗ Invalid: " + message;
    }
}
