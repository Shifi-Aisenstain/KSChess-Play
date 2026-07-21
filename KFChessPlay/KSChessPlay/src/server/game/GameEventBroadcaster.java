package server.game;

import server.rooms.Room;
import server.rooms.RoomManager;
import shared.eventbus.EventBus;
import shared.eventbus.events.GameEndedEvent;
import shared.eventbus.events.GameStartedEvent;
import shared.eventbus.events.MoveExecutedEvent;
import shared.eventbus.events.PieceJumpedEvent;
import shared.protocol.MessageType;
import shared.protocol.payload.GameEventPayload;

/**
 * The other domain-event subscriber alongside {@link server.logging.ServerActivityLogger}:
 * turns the same bus events into lightweight {@code GAME_EVENT} pushes so
 * clients can trigger sound/animation, without waiting for (or parsing) a
 * full {@code STATE_UPDATE} snapshot.
 */
public final class GameEventBroadcaster {
    private final RoomManager roomManager;

    public GameEventBroadcaster(EventBus eventBus, RoomManager roomManager) {
        this.roomManager = roomManager;
        eventBus.subscribe(GameStartedEvent.class, e -> broadcast(e.roomId,
                new GameEventPayload(GameEventPayload.Kind.GAME_STARTED, '\0', '\0', '\0',
                        e.whiteUsername + " vs " + e.blackUsername + " - game on!", 0, 0, "")));

        eventBus.subscribe(GameEndedEvent.class, e -> broadcast(e.roomId,
                new GameEventPayload(GameEventPayload.Kind.GAME_ENDED, '\0', '\0', '\0',
                        e.winnerColor == null ? "Game over"
                                : winnerName(e) + " wins by " + e.reason, 0, 0, "")));

        eventBus.subscribe(MoveExecutedEvent.class, e -> broadcast(e.roomId,
                new GameEventPayload(
                        e.capturedPiece != null ? GameEventPayload.Kind.PIECE_CAPTURED : GameEventPayload.Kind.MOVE_EXECUTED,
                        e.movedPiece.getColor(), e.movedPiece.getType(),
                        e.capturedPiece != null ? e.capturedPiece.getType() : '\0',
                        e.notation, e.scoreWhite, e.scoreBlack, e.logEntry)));

        eventBus.subscribe(PieceJumpedEvent.class, e -> broadcast(e.roomId,
                new GameEventPayload(GameEventPayload.Kind.PIECE_JUMPED, e.piece.getColor(), e.piece.getType(), '\0',
                        "jump", 0, 0, "")));
    }

    private void broadcast(String roomId, GameEventPayload payload) {
        roomManager.getRoom(roomId).ifPresent(room -> room.broadcastToAll(MessageType.GAME_EVENT, payload));
    }

    private static String winnerName(GameEndedEvent e) {
        return "WHITE".equals(e.winnerColor) ? e.whiteUsername : e.blackUsername;
    }
}
