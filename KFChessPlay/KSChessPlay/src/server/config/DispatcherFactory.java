package server.config;

import server.auth.AuthService;
import server.dispatch.MessageDispatcher;
import server.dispatch.handlers.JumpHandler;
import server.dispatch.handlers.LoginHandler;
import server.dispatch.handlers.MoveHandler;
import server.dispatch.handlers.PingHandler;
import server.dispatch.handlers.PlayCancelHandler;
import server.dispatch.handlers.PlayRequestHandler;
import server.dispatch.handlers.ResignHandler;
import server.dispatch.handlers.RoomCancelHandler;
import server.dispatch.handlers.RoomCreateHandler;
import server.dispatch.handlers.RoomJoinHandler;
import server.dispatch.handlers.SelectHandler;
import server.matchmaking.MatchmakingService;
import server.rooms.RoomManager;
import shared.protocol.MessageType;

/** Registers every {@code MessageType} -> handler mapping. The one place that needs to know the
 *  full set of wire message types the server understands. */
public final class DispatcherFactory {
    private DispatcherFactory() { }

    public static MessageDispatcher build(AuthService authService, MatchmakingService matchmakingService,
                                           RoomManager roomManager) {
        MessageDispatcher dispatcher = new MessageDispatcher();
        dispatcher.register(MessageType.LOGIN, new LoginHandler(authService, roomManager));
        dispatcher.register(MessageType.PLAY_REQUEST, new PlayRequestHandler(matchmakingService, roomManager));
        dispatcher.register(MessageType.PLAY_CANCEL, new PlayCancelHandler(matchmakingService));
        dispatcher.register(MessageType.ROOM_CREATE, new RoomCreateHandler(roomManager));
        dispatcher.register(MessageType.ROOM_JOIN, new RoomJoinHandler(roomManager));
        dispatcher.register(MessageType.ROOM_CANCEL, new RoomCancelHandler(roomManager));
        dispatcher.register(MessageType.SELECT, new SelectHandler(roomManager));
        dispatcher.register(MessageType.MOVE, new MoveHandler(roomManager));
        dispatcher.register(MessageType.JUMP, new JumpHandler(roomManager));
        dispatcher.register(MessageType.RESIGN, new ResignHandler(roomManager));
        dispatcher.register(MessageType.PING, new PingHandler());
        return dispatcher;
    }
}
