package server;

import server.auth.AuthService;
import server.auth.AuthServiceImpl;
import server.auth.PasswordHasher;
import server.auth.SqliteUserRepository;
import server.auth.UserRepository;
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
import server.game.GameEventBroadcaster;
import server.logging.ServerActivityLogger;
import server.matchmaking.EloRangeMatchCriteria;
import server.matchmaking.MatchmakingService;
import server.matchmaking.MatchmakingServiceImpl;
import server.net.ChessWebSocketServer;
import server.net.SessionRegistry;
import server.persistence.SchemaInitializer;
import server.persistence.SqliteConnectionProvider;
import server.rating.EloRatingService;
import server.rating.RatingService;
import server.rooms.RoomManager;
import shared.eventbus.EventBus;
import shared.protocol.MessageType;

import java.net.InetSocketAddress;

/**
 * Composition root: the only class that "new"s every concrete component and
 * wires interfaces to implementations. Nothing below this file reaches
 * upward to construct its own collaborators - that's what keeps every other
 * class swappable/testable in isolation (Dependency Injection by hand,
 * no framework needed for a project this size).
 */
public final class ServerMain {
    private static final int PORT = 8887;
    private static final String DB_FILE = "kungfu_chess.db";
    private static final String ACTIVITY_LOG_FILE = "server_activity.log";
    private static final int ELO_MATCH_RANGE = 100;

    public static void main(String[] args) {
        EventBus eventBus = new EventBus();

        SqliteConnectionProvider connectionProvider = new SqliteConnectionProvider(DB_FILE);
        new SchemaInitializer(connectionProvider).initialize();
        UserRepository userRepository = new SqliteUserRepository(connectionProvider);
        AuthService authService = new AuthServiceImpl(userRepository, new PasswordHasher());

        RatingService ratingService = new EloRatingService();
        RoomManager roomManager = new RoomManager(eventBus, ratingService, userRepository);
        MatchmakingService matchmakingService = new MatchmakingServiceImpl(
                new EloRangeMatchCriteria(ELO_MATCH_RANGE), roomManager);

        ServerActivityLogger activityLogger = new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);
        new GameEventBroadcaster(eventBus, roomManager); // subscribes itself; no further reference needed
        SessionRegistry sessionRegistry = new SessionRegistry(activityLogger);

        MessageDispatcher dispatcher = buildDispatcher(authService, matchmakingService, roomManager);

        ChessWebSocketServer server = new ChessWebSocketServer(
                new InetSocketAddress(PORT), dispatcher, sessionRegistry, roomManager, matchmakingService, activityLogger);
        server.start();
        System.out.println("KungFu Chess server started on port " + PORT);
    }

    private static MessageDispatcher buildDispatcher(AuthService authService,
                                                       MatchmakingService matchmakingService,
                                                       RoomManager roomManager) {
        MessageDispatcher dispatcher = new MessageDispatcher();
        dispatcher.register(MessageType.LOGIN, new LoginHandler(authService));
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
