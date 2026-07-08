package Controller;

import Models.Board;
import Models.Piece;

import java.util.ArrayList;
import java.util.List;

public class GameEvent {
    public enum EventType { MOVE, JUMP }

    private final EventType type;
    private final Piece piece;
    private final int fromRow, fromCol, toRow, toCol;
    private final long endTime;

    public GameEvent(EventType type, Piece piece, int fromRow, int fromCol, int toRow, int toCol, long endTime) {
        this.type = type;
        this.piece = piece;
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
        this.endTime = endTime;
    }

    // Getters
    public EventType getType() { return type; }
    public Piece getPiece() { return piece; }
    public int getFromRow() { return fromRow; }
    public int getFromCol() { return fromCol; }
    public int getToRow() { return toRow; }
    public int getToCol() { return toCol; }
    public long getEndTime() { return endTime; }

    public static class GameManager {
        private final Board board;
        private final List<GameEvent> activeEvents = new ArrayList<>();
        private long gameClockMs = 0;
        private boolean isGameOver = false;

        public GameManager(int rows, int cols) {
            this.board = new Board(rows, cols);
        }

        public void handleWait(int ms) {
            this.gameClockMs += ms;
            updateGame();
        }

        private void updateGame() {
            // רשימה זמנית של אירועים שהגיעו לסיומם בשנייה זו
            List<GameEvent> triggeredEvents = new ArrayList<>();

            for (GameEvent event : activeEvents) {
                if (gameClockMs >= event.getEndTime()) {
                    triggeredEvents.add(event);
                }
            }

            for (GameEvent event : triggeredEvents) {
                if (event.getType() == EventType.MOVE) {
                    handleMoveArrival(event);
                } else if (event.getType() == EventType.JUMP) {
                    // קפיצה הסתיימה ואף אחד לא הפריע לה? הכלי פשוט נוחת חזרה
                    activeEvents.remove(event);
                }
            }
        }

        private void handleMoveArrival(GameEvent moveEvent) {
            int toRow = moveEvent.getToRow();
            int toCol = moveEvent.getToCol();

            // 🎯 בדיקת חוק הקפיצה: האם יש כלי אחר שכרגע נמצא באוויר (JUMP) במשבצת הזו?
            boolean capturedByJumper = false;
            for (GameEvent event : activeEvents) {
                if (event.getType() == EventType.JUMP && event.getFromRow() == toRow && event.getFromCol() == toCol) {
                    // יש פה כלי שקופץ! הכלי המוטס לוכד את הכלי שהגיע.
                    capturedByJumper = true;
                    break;
                }
            }

            if (capturedByJumper) {
                // הכלי שהגיע (הנע) פשוט מוסר (נעלם), הוא לא נכתב על הלוח!
                board.setPieceAt(moveEvent.getFromRow(), moveEvent.getFromCol(), null);
            } else {
                // מהלך רגיל: בדיקת אכילת מלך ונחיתה
                Piece target = board.getPieceAt(toRow, toCol);
                if (target != null && target.getType() == 'K') {
                    this.isGameOver = true;
                }
                board.setPieceAt(toRow, toCol, moveEvent.getPiece());
                board.setPieceAt(moveEvent.getFromRow(), moveEvent.getFromCol(), null);
            }

            activeEvents.remove(moveEvent);
        }

        public void createMove(Piece piece, int fromRow, int fromCol, int toRow, int toCol) {
            long endTime = gameClockMs + 1000;
            activeEvents.add(new GameEvent(EventType.MOVE, piece, fromRow, fromCol, toRow, toCol, endTime));
        }

        public void createJump(Piece piece, int row, int col) {
            long endTime = gameClockMs + 1000;
            // בקפיצה המקור והיעד הם אותה משבצת
            activeEvents.add(new GameEvent(EventType.JUMP, piece, row, col, row, col, endTime));
        }

        // בדיקה האם כלי מסוים כרגע בתנועה (בשביל לחסום קליקים)
        public boolean isPieceMoving(int row, int col) {
            for (GameEvent e : activeEvents) {
                if (e.getType() == EventType.MOVE && e.getFromRow() == row && e.getFromCol() == col) {
                    return true;
                }
            }
            return false;
        }
    }
}