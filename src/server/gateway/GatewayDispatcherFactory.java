package server.gateway;

import server.auth.SessionStore;
import server.dispatch.MessageDispatcher;
import server.dispatch.handlers.IdentifyHandler;
import server.dispatch.handlers.JumpHandler;
import server.dispatch.handlers.MoveHandler;
import server.dispatch.handlers.PingHandler;
import server.dispatch.handlers.PlayCancelHandler;
import server.dispatch.handlers.PlayRequestHandler;
import server.dispatch.handlers.ResignHandler;
import server.dispatch.handlers.RoomCancelHandler;
import server.dispatch.handlers.RoomCreateHandler;
import server.dispatch.handlers.RoomJoinHandler;
import server.dispatch.handlers.SelectHandler;
import shared.metrics.MetricsRegistry;
import shared.protocol.MessageType;

public final class GatewayDispatcherFactory {
    private GatewayDispatcherFactory() { }

    public static MessageDispatcher build(RoomLifecycleClient roomLifecycle, MatchmakingClient matchmakingClient,
                                           GatewayRoomRegistry roomRegistry, SessionStore sessionStore,
                                           MetricsRegistry metricsRegistry) {
        MessageDispatcher dispatcher = new MessageDispatcher();
        dispatcher.register(MessageType.IDENTIFY, new IdentifyHandler(roomLifecycle, roomRegistry, sessionStore, metricsRegistry));
        dispatcher.register(MessageType.PLAY_REQUEST, new PlayRequestHandler(matchmakingClient, roomRegistry));
        dispatcher.register(MessageType.PLAY_CANCEL, new PlayCancelHandler(matchmakingClient));
        dispatcher.register(MessageType.ROOM_CREATE, new RoomCreateHandler(roomLifecycle, roomRegistry));
        dispatcher.register(MessageType.ROOM_JOIN, new RoomJoinHandler(roomLifecycle, roomRegistry));
        dispatcher.register(MessageType.ROOM_CANCEL, new RoomCancelHandler(roomLifecycle));
        dispatcher.register(MessageType.SELECT, new SelectHandler(roomRegistry));
        dispatcher.register(MessageType.MOVE, new MoveHandler(roomRegistry));
        dispatcher.register(MessageType.JUMP, new JumpHandler(roomRegistry));
        dispatcher.register(MessageType.RESIGN, new ResignHandler(roomRegistry));
        dispatcher.register(MessageType.PING, new PingHandler());
        return dispatcher;
    }
}
