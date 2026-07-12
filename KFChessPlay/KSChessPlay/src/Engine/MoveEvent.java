package Engine;

import Models.Board;
import Models.Piece;
import Models.Position; // 🔥 ייבוא הטיפוס החדש
import java.util.List;

public class MoveEvent extends GameEvent {
    private final int toRow;
    private final int toCol;

    public MoveEvent(Piece piece, int fromRow, int fromCol, int toRow, int toCol, long endTime) {
        super(piece, fromRow, fromCol, endTime);
        this.toRow = toRow;
        this.toCol = toCol;
    }

    public int getToRow() { return toRow; }
    public int getToCol() { return toCol; }

    @Override
    public void execute(Board board, List<GameEvent> activeEvents, GameManager gameManager) {
        int toRow = this.getToRow();
        int toCol = this.getToCol();

        boolean capturedByJumper = false;

        // בדיקה האם יש כלי אויב שקפץ לאותה משבצת יעד בזמן הזה
        for (GameEvent event : activeEvents) {
            if (event instanceof JumpEvent
                    && event.getFromRow() == toRow
                    && event.getFromCol() == toCol
                    && event.getPiece().getColor() != this.piece.getColor()) {
                capturedByJumper = true;
                break;
            }
        }

        // 🔥 התאמה לשימוש ב-Position המאובטח של הלוח
        Position sourcePos = new Position(this.fromRow, this.fromCol);
        Position targetPos = new Position(toRow, toCol);

        if (capturedByJumper) {
            // אם קפצו עליו באוויר - הוא נלכד! מנקים רק את משבצת המוצא שלו והוא לא מגיע ליעד
            board.setPieceAt(sourcePos, null);
        } else {
            // הגעה רגילה ליעד
            Piece target = board.getPieceAt(targetPos);
            if (target != null && target.getType() == 'K') {
                gameManager.setGameOver(true);
            }

            // בדיקת הכתרה של רגלי
            Piece pieceToPlace = this.piece;
            if (this.piece.getType() == 'P') {
                if ((this.piece.getColor() == 'w' && toRow == 0) ||
                        (this.piece.getColor() == 'b' && toRow == board.getLength() - 1)) {
                    pieceToPlace = new Models.Piece(this.piece.getColor(), 'Q');
                }
            }

            board.setPieceAt(targetPos, pieceToPlace);
            board.setPieceAt(sourcePos, null);
        }
    }
}