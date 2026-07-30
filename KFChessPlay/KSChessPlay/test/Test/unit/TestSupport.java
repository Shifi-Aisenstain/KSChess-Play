package Test.unit;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import server.allocator.GameAllocatorRuntime;
import server.allocator.ShardSelector;
import server.auth.UserRepository;
import server.gateway.GatewayRoomRegistry;
import server.gateway.MatchmakingClient;
import server.gateway.RoomLifecycleClient;
import server.logging.ServerActivityLogger;
import server.matchmaking.BusRoomAllocationClient;
import server.matchmaking.EloRangeMatchCriteria;
import server.matchmaking.MatchmakerRuntime;
import server.matchmaking.MatchmakingService;
import server.matchmaking.MatchmakingServiceImpl;
import server.rating.RatingService;
import server.rooms.RoomManager;
import server.shard.GameShardRuntime;
import shared.bus.InMemoryMessageBus;
import shared.bus.MessageBus;
import shared.eventbus.EventBus;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/** Shared test doubles reused across several *Test classes in this package - not production code. */
final class TestSupport {
    /** The one shard id every test harness here uses - there's only ever one GameShardRuntime on
     *  the shared bus in these tests, so {@link ShardSelector} always picks this. */
    private static final String SHARD_ID = "shard-1";

    private TestSupport() { }

    /** A {@link WebSocket} double via a JDK dynamic proxy (the real interface has ~25 methods;
      * only isOpen()/send(String) matter for our handlers, everything else is a safe default). */
    static WebSocket fakeWebSocket(boolean open, List<String> sentJsonOut) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "isOpen": return open;
                case "send":
                    if (args != null && args.length == 1 && args[0] instanceof String) {
                        sentJsonOut.add((String) args[0]);
                    }
                    return null;
                // ConcurrentHashMap (SessionRegistry) keys on identity here, so hashCode/equals must
                // behave like Object's defaults rather than falling through to the boolean/null default.
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                case "toString": return "FakeWebSocket@" + Integer.toHexString(System.identityHashCode(proxy));
                default:
                    return method.getReturnType() == boolean.class ? false : null;
            }
        };
        return (WebSocket) Proxy.newProxyInstance(
                TestSupport.class.getClassLoader(), new Class<?>[]{WebSocket.class}, handler);
    }

    static ServerActivityLogger newActivityLogger() throws Exception {
        return new ServerActivityLogger(
                Files.createTempFile("kfchess-test-server-", ".log").toString(), new EventBus());
    }

    /**
     * Bundles every piece a test needs to exercise the full "monolith + one Game Server Shard + one
     * WS Gateway instance" setup PLAN_split_6_services.md §3.1/§3.2 calls for as those sub-steps' own
     * verification, all wired to the same {@link InMemoryMessageBus} standing in for a live NATS
     * connection (the same substitution {@code MessageBusTest} already makes - see its javadoc).
     *
     * <p>Every test that used to construct a {@code RoomManager}/{@code GameSessionHandle} directly
     * and rely on real move/jump acceptance semantics, or that used to reach a room's
     * {@code GameSession} via {@code Room.getGameSession()}, needs this now: {@code GameSession} runs
     * "elsewhere" (a Shard listening on {@link #roomRegistry}'s bus, reachable only through a
     * {@link server.gateway.GatewayRoomSession} obtained from {@link #roomRegistry}), and the
     * room-lifecycle/matchmaking handlers reach {@link #roomManager}/{@link #matchmakingService}
     * only indirectly, through {@link #roomLifecycle}/{@link #matchmakingClient}'s bus round-trip to
     * {@link MonolithBusGateway} (exactly like the real WS Gateway process does).
     */
    static final class GatewayHarness {
        final MessageBus bus;
        final RoomManager roomManager;
        final MatchmakingService matchmakingService;
        final GatewayRoomRegistry roomRegistry;
        final RoomLifecycleClient roomLifecycle;
        final MatchmakingClient matchmakingClient;

        private GatewayHarness(MessageBus bus, RoomManager roomManager, MatchmakingService matchmakingService,
                                GatewayRoomRegistry roomRegistry, RoomLifecycleClient roomLifecycle,
                                MatchmakingClient matchmakingClient) {
            this.bus = bus;
            this.roomManager = roomManager;
            this.matchmakingService = matchmakingService;
            this.roomRegistry = roomRegistry;
            this.roomLifecycle = roomLifecycle;
            this.matchmakingClient = matchmakingClient;
        }

        /** Test-only cleanup, replacing the old {@code room.getGameSession().handleResign(...)} calls:
         *  force-resigns a started room's GameSession via the same bus command a real WS Gateway would
         *  send on disconnect, so tests don't leak GameSession's background ticking thread. A no-op if
         *  this Gateway-instance-under-test never saw {@code roomId} allocated. */
        void forceResign(String roomId, long userId) {
            roomRegistry.get(roomId).ifPresent(session -> session.forceResignDueToDisconnect(userId));
        }
    }

    static GatewayHarness newGatewayHarness(EventBus monolithEventBus, RatingService ratingService,
                                             UserRepository userRepository) {
        return newGatewayHarness(monolithEventBus, ratingService, userRepository, null);
    }

    /**
     * @param matchmakingServiceOverride test double to use instead of the real
     *                                   {@link MatchmakingServiceImpl} (e.g. a request-recording
     *                                   stub) - {@code null} for the real thing.
     */
    static GatewayHarness newGatewayHarness(EventBus monolithEventBus, RatingService ratingService,
                                             UserRepository userRepository,
                                             MatchmakingService matchmakingServiceOverride) {
        MessageBus bus = new InMemoryMessageBus();
        // GameShardRuntime/GameSession only ever need the narrower EloWriter (Server_Design.md step
        // 5) - a plain method reference adapts the full UserRepository test double callers already
        // pass in, without needing a second, EloWriter-shaped test double everywhere.
        GameShardRuntime shard = new GameShardRuntime(bus, new EventBus(), ratingService, userRepository::updateElo, SHARD_ID);
        shard.start();

        // Subscribes to room.allocated *before* any room can possibly be created below - InMemoryMessageBus
        // does not replay missed publishes to a late subscriber (see MessageBusTest), so construction
        // order here matters exactly as much as it does between the real WsGatewayMain and GameServerShardMain.
        GatewayRoomRegistry roomRegistry = new GatewayRoomRegistry(bus);

        // "The monolith" is really two separate processes now (PLAN_split_6_services.md §3.3/§3.4):
        // the Game Allocator (RoomManager, reached via room.create/room.join like the real
        // GameAllocatorRuntime) and the Matchmaker (MatchmakingServiceImpl, reached only over the bus
        // via BusRoomAllocationClient - never touching roomManager in-process, exactly like the real
        // MatchmakerMain).
        RoomManager roomManager = new RoomManager(monolithEventBus, bus, new ShardSelector(List.of(SHARD_ID)));
        new GameAllocatorRuntime(bus, roomManager).start();

        RoomLifecycleClient roomLifecycle = new RoomLifecycleClient(bus);
        MatchmakingService matchmakingService = matchmakingServiceOverride != null ? matchmakingServiceOverride
                : new MatchmakingServiceImpl(new EloRangeMatchCriteria(100), new BusRoomAllocationClient(roomLifecycle));
        new MatchmakerRuntime(bus, matchmakingService).start();

        MatchmakingClient matchmakingClient = new MatchmakingClient(bus);
        return new GatewayHarness(bus, roomManager, matchmakingService, roomRegistry, roomLifecycle, matchmakingClient);
    }

    /** A minimal real (loopback) WebSocket server, so client-side outgoing code that needs an
      * actually-open socket (ServerConnection/NetworkGameController's requestPlay/handleBoardClick/etc,
      * LoginController.login) can be exercised end-to-end without touching the real server.ServerController
      * or its SQLite/matchmaking dependencies. */
    static final class LoopbackServer extends WebSocketServer {
        final List<String> received = new CopyOnWriteArrayList<>();
        volatile WebSocket lastConnection;

        LoopbackServer() { super(new InetSocketAddress("localhost", 0)); }

        @Override public void onOpen(WebSocket conn, ClientHandshake handshake) { lastConnection = conn; }
        @Override public void onMessage(WebSocket conn, String message) { received.add(message); }
        @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) { }
        @Override public void onError(WebSocket conn, Exception ex) { }
        @Override public void onStart() { }

        void sendToLastConnection(String rawJson) { lastConnection.send(rawJson); }

        /** Blocks briefly until at least {@code count} messages have been received, or times out. */
        void awaitReceived(int count, long timeoutSeconds) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
            while (received.size() < count && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
        }
    }

    static LoopbackServer startLoopbackServer() throws InterruptedException {
        LoopbackServer server = new LoopbackServer();
        server.start();
        // WebSocketServer.start() spins up its selector thread asynchronously; give it a moment to bind.
        Thread.sleep(200);
        return server;
    }
}
