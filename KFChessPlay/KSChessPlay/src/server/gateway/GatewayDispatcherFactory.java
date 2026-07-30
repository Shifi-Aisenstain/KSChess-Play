package server.gateway;

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
import shared.protocol.MessageType;

/**
 * Registers every {@code MessageType} -> handler mapping for the WS Gateway (moved here from
 * {@code server.config.DispatcherFactory} as part of PLAN_split_6_services.md §3.2 - client message
 * dispatch is a Gateway-process concern now, not the monolith's). The one place that needs to know
 * the full set of wire message types the server understands.
 */
public final class GatewayDispatcherFactory {
    private GatewayDispatcherFactory() { }

    public static MessageDispatcher build(RoomLifecycleClient roomLifecycle,
                                           MatchmakingClient matchmakingClient, GatewayRoomRegistry roomRegistry) {
        MessageDispatcher dispatcher = new MessageDispatcher();
        dispatcher.register(MessageType.IDENTIFY, new IdentifyHandler(roomLifecycle, roomRegistry));
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
