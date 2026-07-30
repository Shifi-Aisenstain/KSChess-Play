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
import server.history.GameHistoryRecorder;
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
import shared.metrics.MetricsRegistry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class TestSupport {

    private static final String SHARD_ID = "shard-1";

    private TestSupport() { }

    static WebSocket fakeWebSocket(boolean open, List<String> sentJsonOut) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "isOpen": return open;
                case "send":
                    if (args != null && args.length == 1 && args[0] instanceof String) {
                        sentJsonOut.add((String) args[0]);
                    }
                    return null;

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

    static ServerActivityLogger newActivityLoggerOrThrow() {
        try {
            return newActivityLogger();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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

        void forceResign(String roomId, long userId) {
            roomRegistry.get(roomId).ifPresent(session -> session.forceResignDueToDisconnect(userId));
        }
    }

    static GatewayHarness newGatewayHarness(EventBus monolithEventBus, RatingService ratingService,
                                             UserRepository userRepository) {
        return newGatewayHarness(monolithEventBus, ratingService, userRepository, null);
    }

    static GatewayHarness newGatewayHarness(EventBus monolithEventBus, RatingService ratingService,
                                             UserRepository userRepository,
                                             MatchmakingService matchmakingServiceOverride) {
        MessageBus bus = new InMemoryMessageBus();

        GameShardRuntime shard = new GameShardRuntime(bus, new EventBus(), ratingService, userRepository::updateElo,
                GameHistoryRecorder.NO_OP, SHARD_ID, new MetricsRegistry());
        shard.start();

        GatewayRoomRegistry roomRegistry = new GatewayRoomRegistry(bus);

        RoomManager roomManager = new RoomManager(monolithEventBus, bus, new ShardSelector(List.of(SHARD_ID)));
        new GameAllocatorRuntime(bus, roomManager, newActivityLoggerOrThrow(), new MetricsRegistry()).start();

        RoomLifecycleClient roomLifecycle = new RoomLifecycleClient(bus);
        MatchmakingService matchmakingService = matchmakingServiceOverride != null ? matchmakingServiceOverride
                : new MatchmakingServiceImpl(new EloRangeMatchCriteria(100), new BusRoomAllocationClient(roomLifecycle));
        new MatchmakerRuntime(bus, matchmakingService, newActivityLoggerOrThrow(), new MetricsRegistry()).start();

        MatchmakingClient matchmakingClient = new MatchmakingClient(bus);
        return new GatewayHarness(bus, roomManager, matchmakingService, roomRegistry, roomLifecycle, matchmakingClient);
    }

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

        Thread.sleep(200);
        return server;
    }
}
