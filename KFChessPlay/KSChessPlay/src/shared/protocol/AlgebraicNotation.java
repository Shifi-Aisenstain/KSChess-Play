package shared.protocol;

import models.Position;

/**
 * Encodes/decodes the compact move-command string mandated by the course
 * spec, e.g. {@code "WQe2e5"}:
 * <pre>
 *   W          Q            e2            e5
 *   ^color     ^piece       ^from square  ^to square
 * </pre>
 * Keeping this parsing logic in one utility (instead of sprinkled through
 * handler classes) means the wire format can change without touching any
 * business logic - only this class needs to know the string layout.
 */
public final class AlgebraicNotation {

    private AlgebraicNotation() { }

    public static final class ParsedCommand {
        public final char color;       // 'w' or 'b'
        public final char pieceType;   // K,Q,R,B,N,P
        public final Position from;
        public final Position to;

        public ParsedCommand(char color, char pieceType, Position from, Position to) {
            this.color = color;
            this.pieceType = pieceType;
            this.from = from;
            this.to = to;
        }
    }

    /** @param boardRows needed because rank numbers grow upward from the bottom row. */
    public static ParsedCommand parse(String command, int boardRows) {
        if (command == null || command.length() != 6) {
            throw new IllegalArgumentException("Malformed move command: " + command);
        }
        char color = Character.toLowerCase(command.charAt(0));
        char pieceType = Character.toUpperCase(command.charAt(1));
        Position from = squareToPosition(command.substring(2, 4), boardRows);
        Position to = squareToPosition(command.substring(4, 6), boardRows);
        return new ParsedCommand(color, pieceType, from, to);
    }

    public static String format(char color, char pieceType, Position from, Position to, int boardRows) {
        return Character.toUpperCase(color) + "" + Character.toUpperCase(pieceType)
                + positionToSquare(from, boardRows) + positionToSquare(to, boardRows);
    }

    public static Position squareToPosition(String square, int boardRows) {
        char file = square.charAt(0);
        int rank = Integer.parseInt(square.substring(1));
        int col = file - 'a';
        int row = boardRows - rank;
        return new Position(row, col);
    }

    public static String positionToSquare(Position pos, int boardRows) {
        char file = (char) ('a' + pos.getCol());
        int rank = boardRows - pos.getRow();
        return "" + file + rank;
    }
}
