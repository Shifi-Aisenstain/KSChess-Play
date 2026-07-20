import client.audio.SoundSubscriber;
import client.bridge.NetworkGameController;
import client.logging.ClientActivityLogger;
import client.net.ServerConnection;
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
import shared.protocol.payload.GameOverPayload;
import shared.protocol.payload.LoginResultPayload;
import view.GameLoop;
import view.GameWindow;
import view.ImgRenderer;
import view.SpriteLoader;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Client bootstrap for the networked build: console login, then a Swing
 * home screen (Play / Room), then a live {@link GameWindow} driven entirely
 * by {@code STATE_UPDATE} messages from the server. This class is the
 * client's composition root - see {@code server.ServerMain} for its
 * server-side counterpart. No gameplay logic lives here; it only wires
 * collaborators together.
 */
public class Main {
    private static final String ASSETS_DIR  = "assets";
    private static final String BOARD_IMAGE = ASSETS_DIR + "/board.png";
    private static final String DEFAULT_SERVER_URI = "ws://localhost:8887";
    private static final int LOGIN_TIMEOUT_SECONDS = 10;

    public static void main(String[] args) throws Exception {
        String serverUri = args.length > 0 ? args[0] : DEFAULT_SERVER_URI;

        ClientActivityLogger activityLogger = new ClientActivityLogger("client_activity.log");
        EventBus localEventBus = new EventBus();
        localEventBus.subscribe(shared.protocol.payload.GameEventPayload.class, new SoundSubscriber());

        LoginSession session = connectAndLogin(serverUri, activityLogger, localEventBus);
        if (session == null) return; // login failed / server unreachable, message already printed

        Image background = new Image();
        background.read(BOARD_IMAGE);

        HomeScreen homeScreen = buildHomeScreen(session);
        wireGameLifecycle(session.controller, homeScreen, background, localEventBus);

        homeScreen.setVisible(true);
    }

    /** Bundles the two things a successful login produces: the live controller and the account details. */
    private static final class LoginSession {
        final NetworkGameController controller;
        final LoginResultPayload account;

        LoginSession(NetworkGameController controller, LoginResultPayload account) {
            this.controller = controller;
            this.account = account;
        }
    }

    private static LoginSession connectAndLogin(String serverUri, ClientActivityLogger activityLogger,
                                                 EventBus localEventBus) {
        try {
            LoginConsolePrompt.Credentials credentials = new LoginConsolePrompt().prompt();

            CompletableFuture<LoginResultPayload> loginFuture = new CompletableFuture<>();

            NetworkGameController[] controllerHolder = new NetworkGameController[1];
            ServerConnection connection = new ServerConnection(new URI(serverUri),
                    message -> controllerHolder[0].onMessage(message), activityLogger);
            NetworkGameController controller = new NetworkGameController(connection, localEventBus);
            controllerHolder[0] = controller;
            controller.setOnLoginResult(loginFuture::complete);

            connection.connectBlocking();
            controller.requestLogin(credentials.username, credentials.password);

            LoginResultPayload result = loginFuture.get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!result.success) {
                System.err.println("Login failed: " + result.errorMessage);
                return null;
            }
            System.out.println("Logged in as " + result.username + " (ELO " + result.elo + ")");
            return new LoginSession(controller, result);
        } catch (TimeoutException e) {
            System.err.println("Server did not respond to login in time. Is it running at " + serverUri + "?");
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
            @Override public void onRoomCreateRequested() { controller.requestCreateRoom(); }
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

        controller.setOnFirstStateUpdate(() -> javax.swing.SwingUtilities.invokeLater(() -> {
            homeScreen.setVisible(false);
            launchGameWindow(controller, background, localEventBus);
        }));
    }

    private static void launchGameWindow(NetworkGameController controller, Image background, EventBus localEventBus) {
        BoardMapper mapper = new BoardMapper();
        SpriteLoader spriteLoader = new SpriteLoader();
        ImgRenderer renderer = new ImgRenderer(spriteLoader, background, mapper);

        String whiteLabel = "White";
        String blackLabel = "Black";
        GameWindow window = new GameWindow(background, whiteLabel, blackLabel);
        localEventBus.subscribe(shared.protocol.payload.GameEventPayload.class, new AnimationSubscriber(window));
        localEventBus.subscribe(shared.protocol.payload.GameEventPayload.class, new ScoreboardSubscriber(window));

        // One-time backfill for a client that joins mid-game (e.g. a spectator) and so missed
        // earlier GAME_EVENTs; ongoing score/move-log updates come from ScoreboardSubscriber above.
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

    private static java.util.function.Consumer<GameOverPayload> describeGameOver(GameWindow window) {
        return payload -> {
            String winner = payload.winnerColor == null ? "Draw" : payload.winnerColor;
            String message = "Game Over - " + winner
                    + "\nReason: " + payload.reason
                    + "\nWhite ELO: " + payload.whiteEloNew + " (" + signed(payload.whiteEloDelta) + ")"
                    + "\nBlack ELO: " + payload.blackEloNew + " (" + signed(payload.blackEloDelta) + ")";
            javax.swing.SwingUtilities.invokeLater(() -> window.showGameOver(message));
        };
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }
}
