package server.shard;

import shared.eventbus.EventBus;
import shared.eventbus.events.GameEndedEvent;
import shared.eventbus.events.GameStartedEvent;
import shared.eventbus.events.MoveExecutedEvent;
import shared.eventbus.events.PieceJumpedEvent;
import shared.bus.MessageBus;
import shared.protocol.MessageType;
import shared.protocol.payload.GameEventPayload;

public final class ShardGameEventRelay {

    public ShardGameEventRelay(EventBus eventBus, MessageBus bus) {
        eventBus.subscribe(GameStartedEvent.class, e -> broadcast(bus, e.roomId,
                new GameEventPayload(GameEventPayload.Kind.GAME_STARTED, '\0', '\0', '\0',
                        e.whiteUsername + " vs " + e.blackUsername + " - game on!", 0, 0, "")));

        eventBus.subscribe(GameEndedEvent.class, e -> broadcast(bus, e.roomId,
                new GameEventPayload(GameEventPayload.Kind.GAME_ENDED, '\0', '\0', '\0',
                        e.winnerColor == null ? "Game over"
                                : winnerName(e) + " wins by " + e.reason, 0, 0, "")));

        eventBus.subscribe(MoveExecutedEvent.class, e -> broadcast(bus, e.roomId,
                new GameEventPayload(
                        e.capturedPiece != null ? GameEventPayload.Kind.PIECE_CAPTURED : GameEventPayload.Kind.MOVE_EXECUTED,
                        e.movedPiece.getColor(), e.movedPiece.getType(),
                        e.capturedPiece != null ? e.capturedPiece.getType() : '\0',
                        e.notation, e.scoreWhite, e.scoreBlack, e.logEntry)));

        eventBus.subscribe(PieceJumpedEvent.class, e -> broadcast(bus, e.roomId,
                new GameEventPayload(GameEventPayload.Kind.PIECE_JUMPED, e.piece.getColor(), e.piece.getType(), '\0',
                        "jump", 0, 0, "")));
    }

    private static void broadcast(MessageBus bus, String roomId, GameEventPayload payload) {
        new BusUpdateSink(bus, roomId).broadcastToAll(MessageType.GAME_EVENT, payload);
    }

    private static String winnerName(GameEndedEvent e) {
        return "WHITE".equals(e.winnerColor) ? e.whiteUsername : e.blackUsername;
    }
}
