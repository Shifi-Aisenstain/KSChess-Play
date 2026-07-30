package loadtest;

import client.logging.ClientActivityLogger;
import client.net.ApiGatewayClient;
import client.net.ServerConnection;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.EmptyPayload;
import shared.protocol.payload.IdentifyPayload;
import shared.protocol.payload.LoginResultPayload;
import shared.protocol.payload.MatchFoundPayload;
import shared.protocol.payload.MoveCommandPayload;

import java.net.URI;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class SimulatedPlayer {
    private static final long CONNECT_TIMEOUT_MS = 5000;
    private static final long MATCHMAKING_TIMEOUT_MS = 15000;
    private static final long MOVE_ROUND_TRIP_TIMEOUT_MS = 5000;
    private static final long GAME_OVER_TIMEOUT_MS = 5000;

    private final int playerIndex;
    private final String apiUrl;
    private final String wsUrl;
    private final String username;
    private final String password = "loadtest-password";
    private final ClientActivityLogger activityLogger;

    public SimulatedPlayer(int playerIndex, String apiUrl, String wsUrl, String runId, ClientActivityLogger activityLogger) {
        this.playerIndex = playerIndex;
        this.apiUrl = apiUrl;
        this.wsUrl = wsUrl;
        this.username = "loadtest-" + runId + "-" + playerIndex;
        this.activityLogger = activityLogger;
    }

    public PlayerRunResult run() {
        PlayerRunResult.Builder result = new PlayerRunResult.Builder(playerIndex);
        ServerConnection connection = null;
        try {
            LoginResultPayload login = doLogin(result);
            if (login == null || !login.success) {
                return result.failureReason("login failed").build();
            }

            LinkedBlockingQueue<Message> inbox = new LinkedBlockingQueue<>();
            connection = new ServerConnection(URI.create(wsUrl), inbox::add, activityLogger);
            connection.setConnectionLostTimeout(0);
            connection.connectBlocking(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!connection.isOpen()) {
                return result.failureReason("websocket connect failed").build();
            }

            connection.send(MessageType.IDENTIFY, new IdentifyPayload(login.sessionToken));

            long matchStart = System.currentTimeMillis();
            connection.send(MessageType.PLAY_REQUEST, EmptyPayload.INSTANCE);
            Message matchMessage = pollUntil(inbox, MATCHMAKING_TIMEOUT_MS,
                    MessageType.MATCH_FOUND, MessageType.PLAY_NOT_FOUND);
            long matchmakingLatency = System.currentTimeMillis() - matchStart;

            if (matchMessage == null || matchMessage.getType() != MessageType.MATCH_FOUND) {
                result.matchmaking(false, matchmakingLatency);
                return result.failureReason("no match found within timeout").build();
            }
            result.matchmaking(true, matchmakingLatency);

            MatchFoundPayload matchFound = MessageCodec.decodePayload(matchMessage, MatchFoundPayload.class);
            MoveCommandPayload move = matchFound.assignedColor == 'w'
                    ? new MoveCommandPayload(6, 4, 4, 4)
                    : new MoveCommandPayload(1, 4, 3, 4);

            long moveStart = System.currentTimeMillis();
            connection.send(MessageType.MOVE, move);
            Message afterMove = inbox.poll(MOVE_ROUND_TRIP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            long moveLatency = System.currentTimeMillis() - moveStart;
            result.moveRoundTrip(afterMove != null, moveLatency);

            connection.send(MessageType.RESIGN, EmptyPayload.INSTANCE);
            Message gameOver = pollUntil(inbox, GAME_OVER_TIMEOUT_MS, MessageType.GAME_OVER);
            result.gameEndedCleanly(gameOver != null);

            return result.build();
        } catch (Exception e) {
            return result.failureReason(e.getClass().getSimpleName() + ": " + e.getMessage()).build();
        } finally {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        }
    }

    private LoginResultPayload doLogin(PlayerRunResult.Builder result) throws Exception {
        ApiGatewayClient apiClient = new ApiGatewayClient(apiUrl);
        long loginStart = System.currentTimeMillis();
        LoginResultPayload login = apiClient.login(username, password);
        long loginLatency = System.currentTimeMillis() - loginStart;
        result.login(login.success, loginLatency);
        return login;
    }

    private static Message pollUntil(LinkedBlockingQueue<Message> inbox, long timeoutMs, MessageType... acceptedTypes)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Message message = inbox.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (message == null) return null;
            for (MessageType type : acceptedTypes) {
                if (message.getType() == type) return message;
            }
        }
        return null;
    }
}
