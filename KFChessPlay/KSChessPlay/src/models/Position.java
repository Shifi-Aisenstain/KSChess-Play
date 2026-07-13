package models;

/**
 * ✅ Immutable Position Model
 * 
 * Used to represent board coordinates (row, col).
 * 
 * CRITICAL: This class implements equals() and hashCode() correctly.
 * This allows Position to be used as a key in collections:
 *   - HashMap<Position, Piece>
 *   - HashSet<Position>
 *   - List.contains(position) comparisons
 * 
 * Used by:
 * - RealTimeArbiter.isPieceBusy() - checks Position equality in activeEvents
 * - Board.getPieceAt(Position) - looks up pieces by position
 * - Blocking detection in RuleEngine - compares positions
 * 
 * @author Chess Game Architecture
 */
public class Position {
    private final int row;
    private final int col;

    public Position(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }

    /**
     * ✅ Equality Check
     * 
     * Two positions are equal if they have the same row and column.
     * This allows Position to work correctly in collections.
     * 
     * EXAMPLES:
     *   new Position(0, 0).equals(new Position(0, 0)) → true
     *   new Position(0, 0).equals(new Position(1, 0)) → false
     * 
     * Used by: RealTimeArbiter.isPieceBusy(), Board.getPieceAt()
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Position)) return false;
        Position other = (Position) obj;
        return this.row == other.row && this.col == other.col;
    }

    /**
     * ✅ Hash Code Calculation
     * 
     * CRITICAL: hashCode() MUST be consistent with equals().
     * If two positions are equal, they MUST have the same hashCode.
     * 
     * Formula: 31 * row + col
     * (31 is a prime number, standard for hash functions)
     * 
     * Enables Position to be used as HashMap/HashSet key.
     */
    @Override
    public int hashCode() {
        return 31 * row + col;
    }

    @Override
    public String toString() {
        return "[" + row + "," + col + "]";
    }
}