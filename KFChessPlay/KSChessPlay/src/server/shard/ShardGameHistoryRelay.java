package server.shard;

import server.history.GameHistoryRecorder;
import shared.eventbus.EventBus;
import shared.eventbus.events.MoveExecutedEvent;

public final class ShardGameHistoryRelay {
    public ShardGameHistoryRelay(EventBus eventBus, GameHistoryRecorder historyRecorder) {
        eventBus.subscribe(MoveExecutedEvent.class, e -> historyRecorder.recordMove(e.roomId, e.notation));
    }
}
