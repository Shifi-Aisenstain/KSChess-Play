package Models;

public class PieceFactory {
    public static Piece createPiece(String token) {
        if (token.equals(".")) return null;
        if (token.length() != 2) return null; // הגנה בסיסית

        char color = token.charAt(0);
        char type = token.charAt(1);

        // בדיקת תקינות צבע
        if (color != 'w' && color != 'b') return null;

        switch (type) {
            case 'R': return new Rook(color);
            case 'B': return new Bishop(color);
            case 'Q': return new Queen(color);
            case 'K': return new King(color);
            case 'N': return new Knight(color);
            case 'P': return new Pawn(color);
            default: return null; // טוקן לא מוכר!
        }
    }
}