package Test.unit;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import server.logging.ServerActivityLogger;
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

    /** A minimal real (loopback) WebSocket server, so client-side outgoing code that needs an
      * actually-open socket (ServerConnection/NetworkGameController's requestPlay/handleBoardClick/etc,
      * LoginController.login) can be exercised end-to-end without touching the real server.ServerMain
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
