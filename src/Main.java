import client.audio.SoundSubscriber;
import client.bridge.LoginController;
import client.bridge.NetworkGameController;
import client.logging.ClientActivityLogger;
import client.net.ApiGatewayClient;
import client.net.ServerConnection;
import client.net.ServerMessageListener;
import client.ui.AnimationSubscriber;
import client.ui.HomeScreen;
import client.ui.HomeScreenListener;
import client.ui.LoginConsolePrompt;
import client.ui.ScoreboardSubscriber;
import graphics.Image;
import input.BoardMapper;
import input.CoordinateParser;
import models.Position;
import shared.eventbus.EventBus;
import shared.protocol.payload.GameEventPayload;
import shared.protocol.payload.GameOverPayload;
import shared.protocol.payload.LoginResultPayload;
import view.GameLoop;
import view.GameWindow;
import view.ImgRenderer;
import view.SpriteLoader;

import javax.swing.SwingUtilities;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class Main {
    private static final String ASSETS_DIR  = "assets";
    private static final String BOARD_IMAGE = ASSETS_DIR + "/board.png";
    private static final String DEFAULT_SERVER_URI = "ws://localhost:8887";
    private static final String DEFAULT_API_BASE_URL = "http://localhost:8080";
    private static final int LOGIN_TIMEOUT_SECONDS = 10;

    public static void main(String[] args) throws Exception {
        String serverUri = args.length > 0 ? args[0] : DEFAULT_SERVER_URI;
        String apiBaseUrl = args.length > 1 ? args[1] : DEFAULT_API_BASE_URL;

        ClientActivityLogger activityLogger = new ClientActivityLogger("logs/client/activity.log");
        EventBus localEventBus = new EventBus();
        localEventBus.subscribe(GameEventPayload.class, new SoundSubscriber());

        LoginSession session = connectAndLogin(serverUri, apiBaseUrl, activityLogger, localEventBus);
        if (session == null) return;

        Image background = new Image();
        background.read(BOARD_IMAGE);

        HomeScreen homeScreen = buildHomeScreen(session);
        wireGameLifecycle(session.controller, homeScreen, background, localEventBus);

        homeScreen.setVisible(true);
    }

    private static final class LoginSession {
        final NetworkGameController controller;
        final LoginResultPayload account;

        LoginSession(NetworkGameController controller, LoginResultPayload account) {
            this.controller = controller;
            this.account = account;
        }
    }

    private static LoginSession connectAndLogin(String serverUri, String apiBaseUrl,
                                                 ClientActivityLogger activityLogger, EventBus localEventBus) {
        try {
            LoginConsolePrompt.Credentials credentials = new LoginConsolePrompt().prompt();

            LoginResultPayload authenticated = new ApiGatewayClient(apiBaseUrl)
                    .login(credentials.username, credentials.password);
            if (!authenticated.success) {
                System.err.println("Login failed: " + authenticated.errorMessage);
                return null;
            }

            ServerMessageListener[] listenerHolder = new ServerMessageListener[1];
            ServerConnection connection = new ServerConnection(new URI(serverUri),
                    message -> listenerHolder[0].onMessage(message), activityLogger);

            LoginController loginController = new LoginController(connection);
            listenerHolder[0] = loginController;

            connection.connectBlocking();
            CompletableFuture<LoginResultPayload> identifyFuture = loginController.identify(authenticated);

            LoginResultPayload result = identifyFuture.get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!result.success) {
                System.err.println("Could not attach identity to connection: " + result.errorMessage);
                return null;
            }
            System.out.println("Logged in as " + result.username + " (ELO " + result.elo + ")");

            NetworkGameController controller = new NetworkGameController(connection, localEventBus);
            listenerHolder[0] = controller;
            return new LoginSession(controller, result);
        } catch (TimeoutException e) {
            System.err.println("Gateway did not respond to identify in time. Is it running at " + serverUri + "?");
            return null;
        } catch (java.io.IOException e) {
            System.err.println("Could not reach API Gateway at " + apiBaseUrl + ": " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("Could not connect/login: " + e.getMessage());
            return null;
        }
    }

    private static HomeScreen buildHomeScreen(LoginSession session) {
        HomeScreen homeScreen = new HomeScreen(session.account.username, session.account.elo);
        NetworkGameController controller = session.controller;

        homeScreen.setListener(new HomeScreenListener() {
            @Override public void onPlayRequested() { controller.requestPlay(); }
            @Override public void onPlayCancelled() { controller.cancelPlay(); }
            @Override public void onRoomCreateRequested(String desiredRoomId) { controller.requestCreateRoom(desiredRoomId); }
            @Override public void onRoomJoinRequested(String roomId) { controller.requestJoinRoom(roomId); }
        });
        return homeScreen;
    }

    private static void wireGameLifecycle(NetworkGameController controller, HomeScreen homeScreen,
                                           Image background, EventBus localEventBus) {
        controller.setOnPlayWaiting(homeScreen::showWaitingForMatch);
        controller.setOnPlayNotFound(homeScreen::showMatchNotFound);
        controller.setOnRoomCreated(status -> homeScreen.showRoomCreated(status.roomId));
        controller.setOnRoomError(homeScreen::showRoomError);
        controller.setOnServerError(msg -> System.err.println("[server error] " + msg));

        controller.setOnFirstStateUpdate(() -> SwingUtilities.invokeLater(() -> {
            homeScreen.setVisible(false);
            launchGameWindow(controller, background, localEventBus);
        }));
    }

    private static void launchGameWindow(NetworkGameController controller, Image background, EventBus localEventBus) {
        BoardMapper mapper = new BoardMapper();
        SpriteLoader spriteLoader = new SpriteLoader();
        ImgRenderer renderer = new ImgRenderer(spriteLoader, background, mapper);

        GameWindow window = new GameWindow(background, controller.getWhiteUsername(), controller.getBlackUsername());
        localEventBus.subscribe(GameEventPayload.class, new AnimationSubscriber(window));
        localEventBus.subscribe(GameEventPayload.class, new ScoreboardSubscriber(window));

        var initialSnapshot = controller.getLatestSnapshot();
        if (initialSnapshot != null) window.updateSidePanel(initialSnapshot);

        window.onClick((x, y) -> {
            Position pos = CoordinateParser.parseClick(x, y, controller.getBoardRows(), controller.getBoardCols());
            if (pos != null) controller.handleBoardClick(pos.getRow(), pos.getCol());
        });
        window.onRightClick((x, y) -> {
            Position pos = CoordinateParser.parseClick(x, y, controller.getBoardRows(), controller.getBoardCols());
            if (pos != null) controller.handleBoardRightClick(pos.getRow(), pos.getCol());
        });

        controller.setOnDisconnectCountdown(payload -> {
            String colorLabel = payload.disconnectedColor == 'w' ? "White" : "Black";
            if (payload.secondsRemaining <= 0) window.hideDisconnectCountdown();
            else window.showDisconnectCountdown(colorLabel, payload.secondsRemaining);
        });

        controller.setOnGameOver(Main.describeGameOver(window));

        GameLoop loop = new GameLoop(controller, renderer, window, 60);
        loop.start();

        window.setVisible(true);
    }

    private static Consumer<GameOverPayload> describeGameOver(GameWindow window) {
        return payload -> {
            String winner = "WHITE".equals(payload.winnerColor) ? payload.whiteUsername
                    : "BLACK".equals(payload.winnerColor) ? payload.blackUsername
                    : "Draw";
            String message = "Game Over - " + winner
                    + "\nReason: " + payload.reason
                    + "\nWhite ELO: " + payload.whiteEloNew + " (" + signed(payload.whiteEloDelta) + ")"
                    + "\nBlack ELO: " + payload.blackEloNew + " (" + signed(payload.blackEloDelta) + ")";
            SwingUtilities.invokeLater(() -> window.showGameOver(message));
        };
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }
}
