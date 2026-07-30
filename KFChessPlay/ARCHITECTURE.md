# KungFu Chess — Architecture Reference

Standalone architecture summary for AI assistants. Read this before exploring the codebase — it should
cover enough for architecture questions, bug hunting, and feature work without re-reading every file.
Verify specifics with Read/Grep before relying on them for anything destructive (renamed/moved code,
line numbers) — this doc can drift from the code over time.

**Written:** 2026-07-19, based on the working tree at that time (mid-refactor from single-player to
networked client/server — see §9). **Updated:** 2026-07-21 — the local single-player code path
(`controller.GameController`, `controller.ConsoleIO`, `input.InteractionManager`, `io.BoardParser`) and
other confirmed-dead classes (`models.GameState`, `realtime.Motion`, `view.ImageView`,
`view.JumpHighlight`, `io.BoardPrinter`) were **deleted outright**, along with their dedicated tests —
this is no longer "dead code to be aware of," it's gone from the tree. Lombok was also added (see §1,
§11). **Updated again 2026-07-22** — reviewed against a set of course slides on server architecture
(Event Bus, Command vs Event, "input is not authorization," Snapshot immutability, disconnect/reconnect)
and fixed the one real gap found (reconnection never actually cancelled the auto-resign timer — see §6).
Also: the wire move format changed from a packed string (`"WQe2e5"`) to structured JSON fields, room
creation now accepts a caller-chosen custom ID, the client login round-trip moved to its own
`LoginController`, and the game window title now shows both player names. See §12 for the full writeup
of this pass. **Updated again 2026-07-24** — added the README that didn't exist, made the run scripts
portable instead of hardcoded to one machine, fixed Maven's test wiring (`mvn test` was silently
running zero tests) and added JaCoCo, fixed a real bug in one pre-existing test, and added ~90 new
tests across previously-untested packages (`shared.protocol`, `shared.eventbus`, `server.auth`,
`server.rating`, `server.matchmaking`, `server.rooms`, `io`, `client.bridge`). See §13.

## 0. Project identity & repo layout

- Real-time ("Kung Fu") chess: **no turns**. Both colors can have pieces moving/cooling-down
  simultaneously; a move's legality and outcome depend on timing, not alternating turns.
- Git root: `C:\Users\User\Desktop\studies\bootcamp\KSChess-Play` (one level above this file's directory).
- This file lives at `KFChessPlay\ARCHITECTURE.md`. The actual Maven module is
  `KFChessPlay\KSChessPlay\` (`pom.xml` there). **Source root is `KSChessPlay\src\` directly** (no
  `src/main/java` — package root starts right under `src/`, e.g. `src/models/Board.java` is class
  `models.Board`).
- Tests live in `KSChessPlay\test\Test\unit\` and `KSChessPlay\test\Test\integration\` — a **sibling**
  of `src/`, not nested under it (see §8 for why this matters for `mvn test`).
- Assets live in `KSChessPlay\assets\` (piece sprites, board image, board.csv starting position).
- Build: Maven, Java 17, produces two shaded jars (client + server) from one codebase — see §8.
- No pre-existing README/CLAUDE.md existed before this doc was written.
- **`KSChessPlay\Dockerfile` / `KSChessPlay\docker-compose.yml`** (added 2026-07-29) — steps 1-2 of
  `Server_Design.md`'s build-out plan (§3): containerize the current monolith server as-is, and a
  compose stack that also runs real Redis + Postgres containers alongside it. The app itself is
  **not** wired into Redis/Postgres yet (still SQLite) — that's a later step in the plan (§3.4-5),
  which depends on the actual service split (§3.3) that hasn't happened yet. See `README.md`'s
  "Docker" section.

## 1. Tech stack & key dependencies (`pom.xml`)

- `org.java-websocket:Java-WebSocket:1.5.4` — WebSocket transport (both client and server side).
- `com.google.code.gson:gson:2.10.1` — JSON (de)serialization for the wire protocol. Plain
  `new GsonBuilder().create()`, no custom adapters/naming policy.
- `org.xerial:sqlite-jdbc:3.44.1.0` — user accounts / auth / Elo persistence.
- `org.junit.jupiter:junit-jupiter:5.10.1` — test scope only.
- `org.projectlombok:lombok:1.18.46` — `provided` scope. Used conservatively: `@AllArgsConstructor` on
  simple public-final-field DTOs (all of `shared/protocol/payload/*`, `shared/eventbus/events/*`,
  `view.PieceSnapshot`) to replace hand-written boilerplate constructors *without changing the public
  field-access API* (call sites still do `payload.username`, not `payload.getUsername()`); `@Getter` +
  `@AllArgsConstructor` on the two classes that already used private-field/getter style
  (`controller.MoveCommand`, `view.CooldownHighlight`) — a 1:1 behavior-preserving swap; and `@Value`
  (full immutable value semantics: private final fields, getters, constructor, `equals`/`hashCode`,
  `toString`) on `models.Position` and `models.Piece`, verified safe by grepping for any
  identity-based (`==`) or default-`equals` comparisons on `Piece` before applying — there were none.
  **Manual `javac` builds need explicit flags** — plain `-cp <lombok.jar>` does *not* trigger annotation
  processing on this environment's JDK (23); it silently no-ops (classes compile but Lombok-generated
  members never materialize, which then fails downstream with "cannot find symbol"/"actual and formal
  argument lists differ in length" everywhere Lombok output was expected). The fix:
  `-processorpath <lombok.jar> -processor "lombok.launch.AnnotationProcessorHider$AnnotationProcessor"`
  in addition to putting the jar on `-cp`. This is **only a manual-`javac` workaround** — a real
  `mvn compile`, or IntelliJ with the Lombok plugin + annotation processing enabled
  (Preferences → Build → Compiler → Annotation Processors), does not need these flags.
- `maven-shade-plugin` builds two fat jars at `package`: classifier `client` (main class `Main`) and
  classifier `server` (main class `server.ServerMain`).

## 2. Package map

| Package | Responsibility |
|---|---|
| `models` | Pure domain data: `Position`, `Piece` (both `@Value` — Lombok), `Board`. |
| `controller` | Just `MoveCommand` now (`@Getter @AllArgsConstructor`) — the request DTO `GameManager.requestMove` takes. The local-play classes that used to live here (`GameController`, `ConsoleIO`) were deleted 2026-07-21 — see §9. |
| `engine` | The real-time simulation core: `GameManager` (orchestrator), `GameEvent`/`MoveEvent`/`JumpEvent`/`CooldownEvent`, `MoveLogger`. |
| `realtime` | `RealTimeArbiter` (the concurrency/cooldown scheduler). |
| `rules` | Stateless move validation: `RuleEngine`, `PieceRules`, `MoveValidation`. No board mutation. |
| `graphics` | `Image` — JDK `ImageIO`/`Graphics2D` wrapper (sprite loading, transparency, drawing). |
| `input` | Click→board-coordinate mapping: `CoordinateParser`, `BoardMapper`. (`InteractionManager`, local-mode-only, was deleted 2026-07-21 — see §9.) |
| `io` | Board loading: just `CsvBoardLoader` now (real game format, `assets/board.csv`). `BoardParser`/`BoardPrinter` were deleted 2026-07-21 — see §9. |
| `view` | Rendering only, no simulation: `GameWindow`, `GameLoop`, `ImgRenderer`, `SpriteLoader`, `GameSnapshot`/`PieceSnapshot`/`SnapshotSource`, `CooldownHighlight`. (`ImageView`/`JumpHighlight` deleted 2026-07-21 — see §9.) |
| `client` | Networked client composition: `client.net` (`ServerConnection`, `ServerMessageListener`), `client.bridge` (`NetworkGameController`), `client.ui` (`HomeScreen`, `RoomDialog`, `LoginConsolePrompt`, `AnimationSubscriber`, `ScoreboardSubscriber`), `client.audio` (`SoundSubscriber`), `client.logging` (`ClientActivityLogger`). |
| `server` | Everything server-side — see §5 for the full subpackage breakdown. |
| `shared` | The wire contract and pub/sub bus used by both sides: `shared.protocol` (+`payload`), `shared.eventbus` (+`events`). |
| `Main` (default package) | Client composition root / entry point. |

## 3. Core domain model (`models`)

- **`Position`** — `@Value`: immutable `{int row, int col}`, value equality (Lombok-generated). No
  bounds validation of its own.
- **`Piece`** — `@Value`: immutable `{char color ('w'/'b'), char type ('R','K','P','Q','B','N')}`.
  Value-equal now (Lombok-generated `equals`/`hashCode`), but engine code still tracks pieces by board
  position rather than relying on that — nothing depends on two same-color-same-type pieces being
  distinguishable.
- **`Board`** — wraps `Piece[rows][cols]`. `getPieceAt`/`setPieceAt` are bounds-checked;
  **`setPieceAt` on an out-of-bounds `Position` silently no-ops** (used deliberately, e.g. by
  `GameManager.clearPosition`). `getReadOnlyMatrixView()` returns `String[][]` (`"<color><type>"` or
  `"."`) for console printing/tests.

## 4. Real-time engine mechanics — how "no turns, cooldowns" works

This is the heart of the game and the most important section to understand before touching gameplay code.

### Key constants (`realtime/RealTimeArbiter.java`)
```
TIME_PER_CELL_MS = 1000L   // move duration = Chebyshev distance(src,dest) * 1000ms
JUMP_DURATION_MS  = 1000L  // jump "dodge window" duration
LONG_REST_MS      = 3000L  // cooldown after a move lands, at the destination square
SHORT_REST_MS     = 1000L  // additional cooldown after a jump's dodge window ends
```

### The model
There is no turn. Each piece's state (idle / moving / jumping / cooling down) is entirely encoded as
zero or more `GameEvent` entries in `RealTimeArbiter.activeEvents`, keyed by `fromPosition`. A piece is
**busy** (`isPieceBusy(row,col)`) if any active `MoveEvent`, `JumpEvent`, or `CooldownEvent` has that
square as `fromPosition` — this is the *only* gate; there's no separate cooldown timestamp map.

`GameEvent` subclasses and their `getPriority()` (lower runs first when multiple trigger in the same
tick): `MoveEvent`(1) → `JumpEvent`(2) → `CooldownEvent`(3). This ordering is what lets a jump beat an
incoming move landing on the same square in the same tick.

### Time advancement — push-based, no internal thread
Nothing inside `engine`/`realtime` runs on its own thread. Something external calls
`GameManager.handleWait(ms)` → `RealTimeArbiter.advanceTime(ms, gameManager)` →
`gameClockMs += ms` → `updateEvents(gameManager)`:
1. Collect every active event whose `endTime <= gameClockMs` into `triggered`.
2. Sort `triggered` by priority (Move → Jump → Cooldown).
3. Call `event.execute(board, activeEventsSnapshot, gameManager)` on each in order, then remove them
   from `activeEvents`.

**Who drives the clock:** `server.game.GameSession.tick()` calls `gameManager.handleWait(50)` every
`TICK_MS = 50ms` on a dedicated `ScheduledExecutorService` — the server is the single authoritative
clock, ticking 20×/sec per room. (There used to be a second, local/console-driven caller —
`controller.ConsoleIO` reading `wait <ms>` lines from a script — but that whole code path was deleted
2026-07-21; `GameSession` is the only caller now.)

### Example move trace (networked)
1. **Client** — two clicks: `GameWindow` → click handler in `Main.launchGameWindow` →
   `client.bridge.NetworkGameController.handleBoardClick`. First click sets `selectedPosition`, sends
   `SELECT` (server-side highlight only). Second click sends `MOVE`
   (`MoveCommandPayload{fromRow, fromCol, toRow, toCol}` — plain structured JSON; see §12, this used to
   be a packed algebraic string like `"WQe2e5"` via a now-deleted `shared.protocol.AlgebraicNotation`).
2. **Server dispatch** — `ChessWebSocketServer.onMessage` → `MessageDispatcher.dispatch` →
   `server.dispatch.handlers.MoveHandler.handle`: requires a logged-in user + active `GameSession`
   (via `HandlerSupport`), builds `Position from`/`to` directly from the payload's row/col fields, calls
   `gameSession.handleMove(userId, from, to)`.
3. **`GameSession.handleMove`** — rejects if the game is over or the user doesn't own the piece color
   at `from` (`ownsColorAt`, server-authoritative ownership check); else calls
   `gameManager.requestMove(new MoveCommand(from, to))`.
4. **`GameManager.requestMove`** — bails if game over/no board; validates via
   `rules.RuleEngine.validateMove` (bounds, ownership, friendly-fire, geometry/ray/pawn rules); bails
   if `arbiter.isPieceBusy(src)`; otherwise calls `arbiter.registerMove(piece, src, dest)`.
5. **`RealTimeArbiter.registerMove`** — computes Chebyshev distance, `moveDuration = distance * 1000ms`,
   pushes `MoveEvent(piece, src, dest, gameClockMs + moveDuration)`. **The board is not mutated yet** —
   the piece logically stays at `src` (though the renderer interpolates its drawn position — see below).
   `isPieceBusy(src)` now returns true, blocking further commands on that square until the move (and
   its post-move cooldown) finish.
6. **On trigger** (some later tick), `MoveEvent.execute` runs:
   - Checks whether an opposite-color `JumpEvent` is active at the destination *right now*
     (`capturedByJumper`). If so: **the mover is captured mid-flight** —
     `gameManager.clearPosition(fromPosition)` (the moving piece vanishes) and the move never lands.
     This is the mechanic that makes jumping a genuine defensive dodge against an incoming capture.
   - Otherwise, re-validates the path at execution time (not just at request time — the board may have
     changed during travel): ray pieces (R/B/Q) walk to the first blocking square
     (`findFirstBlockingSquare`); non-ray pieces just check if the destination is occupied.
   - Friendly blocker → move silently fizzles, no mutation. Otherwise: pawns promote to Queen on the
     back rank, then `gameManager.executeActualMove(from, effectiveDest, piece)` mutates the board,
     updates `MoveLogger`, detects king capture (→ `isGameOver = true`), publishes `MoveExecutedEvent`
     on the `EventBus`.
   - Then `gameManager.registerLongRestCooldown(piece, effectiveDest)` adds a
     `CooldownEvent(LONG_REST, +3000ms)` **at the destination square**, keeping the piece busy there.
7. **Every tick**, `GameSession.broadcastPersonalizedSnapshots()` calls
   `gameManager.createSnapshot(selected, legalMoves)` per connected user and sends `STATE_UPDATE`.
   `createSnapshot` computes each piece's **render-interpolated position**: for any square with an
   active `MoveEvent`/`JumpEvent`, it linearly interpolates toward the destination based on elapsed
   fraction of the event's duration (jump additionally gets a sine "bounce" height,
   `JUMP_BOUNCE_HEIGHT = 0.3`). It also converts active `CooldownEvent`/`JumpEvent`s into
   `CooldownHighlight` objects the renderer draws as cooldown bars.

### Jump mechanic
`GameManager.requestJump(pos)` validates only that a piece exists there and isn't busy
(`RuleEngine.validateJump`), then `arbiter.registerJump` pushes **both**:
- `JumpEvent(endTime = +1000ms)` — the "dodge window": during this second, any incoming `MoveEvent`
  targeting this square gets nullified (per `capturedByJumper` above).
- `CooldownEvent(SHORT_REST, endTime = +2000ms)` — an extra second of cooldown after the dodge window.

Total busy time after a jump: 2s. `JumpEvent.execute` is a no-op re-placement
(`board.setPieceAt(fromPosition, piece)`) — **jump never relocates the piece**; it's "become briefly
untargetable in place," rendered client-side as a vertical bounce.

## 5. Client architecture

### Entry point (`Main.java`, default package)
Composition root for the **networked-only** client. Flow: `LoginConsolePrompt.prompt()` (console
username/password) → opens `client.net.ServerConnection` with a `client.bridge.LoginController` as its
*initial* message listener (routed through a one-element `ServerMessageListener[]` holder, since the
connection needs a listener before either object fully exists) → `LoginController.login(...)` sends
`LOGIN`, awaits `LOGIN_RESULT` (`CompletableFuture`, 10s timeout) → on success, the holder is repointed
at a freshly-built `client.bridge.NetworkGameController` for the rest of the session (see §12 — login
used to be handled inline by `NetworkGameController` itself; it's a dedicated controller now) → builds
`client.ui.HomeScreen` wired to a `HomeScreenListener` that forwards Play/Room actions into
`NetworkGameController` → on the controller's first `STATE_UPDATE` (`setOnFirstStateUpdate`), hides the
home screen and calls `launchGameWindow`: builds `BoardMapper` + `SpriteLoader` + `ImgRenderer`, a
`GameWindow`, wires mouse clicks through `CoordinateParser.parseClick` →
`controller.handleBoardClick`/`handleBoardRightClick`, wires disconnect-countdown/game-over callbacks,
subscribes an `AnimationSubscriber` to a local `EventBus`, and starts a
`view.GameLoop(controller, renderer, window, 60)`.

**Local/offline single-player doesn't exist anymore.** `controller.GameController`, `controller.ConsoleIO`,
`input.InteractionManager`, and `io.BoardParser` (the local, `GameManager`-owning stack, plus their
dedicated tests and the `test/resources/scripts/` fixtures) were deleted 2026-07-21 since nothing
reachable from `Main.java` used them. `Main.java` has only ever supported the networked path.

### Rendering loop
- **`view/GameLoop.java`** — a `javax.swing.Timer(1000/fps, ...)` (fps=60 from `Main`). Each tick: pulls
  `SnapshotSource.getLatestSnapshot()` (the networked implementation, `NetworkGameController`, just
  returns the last `StateUpdatePayload` received — **no client-side simulation at all**), renders via
  `ImgRenderer.render(snapshot, zoom)`, pushes the frame to `GameWindow.updateCanvas`, updates the
  score/history side panel, repaints.
- **`view/ImgRenderer.render`** draws (in order): background board image scaled by zoom → selected-square
  highlight (yellow) → legal-move highlights (green) → cooldown bars (blue, height ∝ remaining
  fraction) → piece sprites (frame cycles every 150ms, `SpriteLoader.FRAME_COUNT = 5`) at their
  (possibly interpolated) tile position → game-over overlay if applicable.
- **`view/SpriteLoader.getSprite(type,color,state,frame)`** builds
  `assets/<TYPE><COLOR>/states/<state>/sprites/<1-5>.png`, resized to 100×100
  (`BoardMapper.PIXELS_PER_TILE`), cached by `"folder/state/frame"`. **Ignores each state's
  `config.json`** entirely — frame timing/loop metadata in the assets is currently unused; timing is
  hard-coded (`FRAME_DURATION_MS = 150` in `ImgRenderer`).
- **`view/GameWindow`** — Swing `JFrame`: board canvas (`JLabel` + `ImageIcon`, pinned to
  `SwingConstants.LEFT/TOP` alignment — see the zoom/click-mapping note below), mouse-wheel zoom
  (0.5–2.0×), left/right move-history `JTable`s, a banner label (start/end/capture flash messages,
  auto-hides after 2.2s), a countdown label (disconnect auto-resign countdown), score panel + "New
  Game" button. One constructor now: `GameWindow(Image, whiteName, blackName)` — the old single-arg
  constructor (a name-entry dialog for local play) was deleted 2026-07-21 along with the local-play
  code path that was its only caller.
  **Click-mapping fix (2026-07-21):** `canvasLabel` used to default to `JLabel`'s centered icon
  alignment. The side history panels keep a fixed preferred height set once from the *unzoomed* board,
  so zooming out (mouse wheel) shrank the rendered board image below that fixed height —
  `BorderLayout` then stretched the label taller than its icon, and centered icon placement meant the
  image no longer started at pixel `(0,0)` of the label. `GameWindow.onClick`/`onRightClick` divide raw
  `MouseEvent` coordinates by `zoomFactor` assuming `(0,0)` *is* the image's top-left corner — so clicks
  landed on the wrong square at any zoom level below 1.0. Fixed by pinning the label to
  `LEFT`/`TOP` alignment so that assumption always holds, regardless of window/zoom size.

### Networked bridge
- **`client/net/ServerConnection`** (`extends WebSocketClient`) — `send(type, payload)` encodes via
  `MessageCodec.encode` and logs via `ClientActivityLogger`; `onMessage` decodes and forwards to a
  single `ServerMessageListener`.
- **`client/bridge/NetworkGameController`** (`implements ServerMessageListener, SnapshotSource`) — the
  hub. Outgoing: `requestLogin`, `requestPlay`, `cancelPlay`, `requestCreateRoom`, `requestJoinRoom`,
  `resign`, `handleBoardClick`/`handleBoardRightClick` → typed `MessageType` sends. Incoming: switches
  on `message.getType()` into callbacks (`onLoginResult`, `onPlayWaiting`, `onMatchFound`,
  `onPlayNotFound`, `onRoomCreated`/`onRoomJoined`, `onDisconnectCountdown`, `onGameOver`,
  `onServerError`) or state (`handleStateUpdate` stores the latest snapshot/color/board size, fires
  `onFirstStateUpdate` once). **`GAME_EVENT` messages are decoded and republished onto the client's
  local `EventBus`** — the decoupling point for sound/animation (see §7).
- **`client/ui/HomeScreen`** — Play/Room buttons (Play toggles to Cancel); Room opens `RoomDialog`
  (Create/Join/Cancel).
- **`client/audio/SoundSubscriber`** / **`client/ui/AnimationSubscriber`** — both
  `implements Subscriber<GameEventPayload>`, subscribed to the client's local `EventBus`.
  `SoundSubscriber` beeps (`Toolkit.beep()` — explicit placeholder for real audio) on
  GAME_STARTED/GAME_ENDED/PIECE_CAPTURED. `AnimationSubscriber` flashes `GameWindow` banners for the
  same event kinds.
- **`client/logging/ClientActivityLogger`** — appends timestamped lines to `client_activity.log`.

## 6. Server architecture & full connection lifecycle

> **Stale as of 2026-07-30 — read §15 first.** Everything below describes the original single-process
> server (`server.ServerMain`/`ServerController`). That process no longer exists: the server has been
> split into five independent processes (Game Server Shard, WS Gateway, Matchmaker, Game Allocator,
> and a still-to-come API Gateway) per `PLAN_split_6_services.md`. §15 has the current process map;
> the sections below are kept as a still-broadly-accurate description of the domain logic itself
> (`GameSession`, `RoomManager`, matchmaking's pairing algorithm, the protocol/event bus) - just not of
> which process owns what anymore.

### Composition root (`server/ServerMain.java`)
Port `8887`, db file `kungfu_chess.db`, log `server_activity.log`, Elo match range ±100. Builds one
shared `EventBus`; wires `SqliteConnectionProvider` → `SchemaInitializer.initialize()` →
`SqliteUserRepository` → `AuthServiceImpl`; `EloRatingService`;
`RoomManager(eventBus, ratingService, userRepository)`;
`MatchmakingServiceImpl(EloRangeMatchCriteria(100), roomManager)`; `ServerActivityLogger` (self-
subscribes to the bus); `GameEventBroadcaster` (self-subscribes, side-effecting constructor);
`SessionRegistry`; a `MessageDispatcher` mapping every `MessageType` to one handler (see below); then
starts `ChessWebSocketServer`.

### Transport (`server/net`)
- **`ChessWebSocketServer`** (`extends WebSocketServer`) — `onOpen` registers a `PlayerSession`;
  `onMessage` decodes the envelope, logs, calls `dispatcher.dispatch(session, message)`; `onClose`
  removes the session, cancels pending matchmaking, and if the user was an active player, detaches
  their sink and starts a `DisconnectWatchdog`.
- **`PlayerSession implements MessageSink`** — per-socket state (`User`, `currentRoomId`); `send` guards
  on `connection.isOpen()`. (No longer holds a watchdog reference — see reconnection note below.)
- **`SessionRegistry`** — `ConcurrentHashMap<WebSocket, PlayerSession>`.
- **`MessageSink`** — the `send(type, payload)` abstraction so `rooms`/`game`/`matchmaking` never need
  to import the WebSocket library directly.

### Dispatch (`server/dispatch`)
`MessageDispatcher` (`EnumMap<MessageType, MessageHandler>`) catches any handler's `RuntimeException`
and turns it into an `ERROR` reply instead of crashing the socket thread.
**`handlers/HandlerSupport`** (package-private) — shared guards used by nearly every handler:
`requireUser(session)` (sends `ERROR "You must log in first."` if not logged in) and
`requireActiveGame(roomManager, session)` (sends `ERROR "You are not in a room."` /
`"Game has not started yet."` as appropriate). Handlers: `LoginHandler`, `PlayRequestHandler`,
`PlayCancelHandler`, `RoomCreateHandler`, `RoomJoinHandler`, `RoomCancelHandler`, `SelectHandler`,
`MoveHandler`, `JumpHandler`, `ResignHandler`, `PingHandler` — one per `MessageType`.

### Game runtime (`server/game`)
- **`GameSession`** — one per active match. `start()` loads `assets/board.csv` via `CsvBoardLoader`
  (falls back to empty 8×8 on IOException), publishes `GameStartedEvent`, starts a single-threaded
  `ScheduledExecutorService` ticking every `TICK_MS = 50`. `tick()`: `gameManager.handleWait(50)` →
  `broadcastPersonalizedSnapshots()` → checks `isGameOver()` for checkmate → `finishGame(...)`.
  `handleMove`/`handleJump` verify `ownsColorAt(userId, position)` before delegating to `gameManager`
  (server-authoritative — prevents playing the opponent's pieces). `handleResign` and
  `forceResignDueToDisconnect` both funnel into `finishGame` (synchronized, idempotent via an `over`
  flag): stops the ticker, computes Elo deltas, persists them, publishes `GameEndedEvent`, broadcasts
  `GAME_OVER`.
- **`GameEventBroadcaster`** — subscribes to `GameStartedEvent`/`GameEndedEvent`/`MoveExecutedEvent`/
  `PieceJumpedEvent` and re-emits each as a lightweight `GAME_EVENT` to the relevant room only — feeds
  client-side sound/animation without waiting on the heavier `STATE_UPDATE`.
- **`DisconnectWatchdog`** — one per disconnect; 20-second countdown, ticks every 1s broadcasting
  `DISCONNECT_COUNTDOWN`, calls `forceResignDueToDisconnect` at zero. Self-cancels if the game already
  ended, **or if the disconnected player reconnects in time** (fixed 2026-07-22 — see below; previously
  `cancel()` existed but nothing ever called it).
  `GameSession.watchdogsByUserId` (added 2026-07-22) is where a running watchdog is registered
  (`registerWatchdog`, called from `ChessWebSocketServer.startDisconnectWatchdogIfNeeded`) and later
  found again to be cancelled (`cancelWatchdogIfPresent`, called from `LoginHandler` — see below).

### Reconnection (added 2026-07-22 — was a gap, see §12)
A disconnect starting the 20s countdown is not, by itself, an automatic loss — if the same user logs
back in before it reaches zero, the game resumes exactly where it left off. This needed three pieces
of previously-missing state, since a reconnect always creates a brand-new `PlayerSession` with no
memory of the old one:
- **`RoomManager.activeRoomByUserId`** (`Map<Long, String>`) — populated in `startGameSession` (both
  colors → the room ID) the moment a match actually begins, and cleared via a `GameEndedEvent`
  subscription in the constructor once it ends. `getActiveRoomForUser(userId)` is the lookup
  `LoginHandler` uses.
- **`GameSession.watchdogsByUserId`** — see above; lets a login find and cancel a specific user's
  in-flight watchdog, since the watchdog itself was created by a now-discarded `PlayerSession`/socket.
- **`LoginHandler.reattachToActiveGameIfAny`** — runs after every successful login (new account or
  returning): looks up `activeRoomByUserId`, and if the user has a live (non-`over`) game, silently
  re-attaches their `MessageSink` (`room.attachSink`), restores `session.currentRoomId`, and cancels
  their watchdog (which also broadcasts `DISCONNECT_COUNTDOWN` with `secondsRemaining=0` so every
  client's countdown UI clears). No client-side change was needed for this — the client's existing
  `onFirstStateUpdate` hook (see §5) already treats "first `STATE_UPDATE` after login" as "show the game
  window," which is exactly what happens once the sink is silently reattached.

### Rooms (`server/rooms`)
- **`Room`** — `sinks`/`roles` (`WHITE|BLACK|SPECTATOR`)/`users` maps + the attached `GameSession`.
  `reserveRole` assigns WHITE → BLACK → SPECTATOR in join order, idempotently.
- **`RoomManager`** — `createRoom(creator, sink, desiredRoomId)` (manual room flow, no session until
  2nd join; `desiredRoomId` lets the creator pick a custom room ID/name instead of always getting an
  auto-generated one — blank/null still auto-generates, and a taken custom ID is rejected via
  `CreateResult.fail(...)`, checked atomically with `ConcurrentHashMap.putIfAbsent` so two racing
  creators can't both win the same custom ID — added 2026-07-22, see §12),
  `joinRoom` (starts the `GameSession` the instant the second player reserves BLACK),
  `createRoomForMatch` (matchmaking flow — both roles reserved immediately, session starts right away),
  `attachSink` (matchmaking's second step once the matched socket is ready), `abandonRoom` (only
  before game start).
- **`RoomIdGenerator`** — 6-char codes from a 32-symbol alphabet excluding `0/O/1/I`.

### Matchmaking (`server/matchmaking`)
`MatchmakingServiceImpl` runs a background scan every `SCAN_INTERVAL_MS = 500` over a
`CopyOnWriteArrayList<MatchRequest>`: expires stale requests (`WAIT_TIMEOUT_MS = 60_000` →
`onNotFound()`), pairs waiting requests via `MatchCriteria.matches` (first-fit, O(n²)), first-enqueued
becomes White. `EloRangeMatchCriteria` — `|eloA - eloB| <= range` (100) and different users.

### Auth/persistence/rating/logging
- **`AuthServiceImpl.login`** — **auto-registers on first login** (no separate signup): unknown
  username creates an account at `STARTING_ELO = 1200`; known username must match the stored hash via
  `PasswordHasher` (`SecureRandom` 16-byte salt, `SHA-256(salt || password)`, Base64 — explicitly
  documented as not bcrypt/argon2-grade, just "not plaintext").
- **`UserRepository`/`SqliteUserRepository`** — single `users` table
  (`id, username, password_hash, salt, elo, created_at`), created idempotently by `SchemaInitializer`.
  `SqliteConnectionProvider` opens a fresh JDBC connection per call, no pooling.
- **`RatingService`/`EloRatingService`** — standard Elo, K-factor 32.
- **`ServerActivityLogger`** — subscribes to the same 4 domain events (human-readable play-by-play log)
  and also exposes `logTraffic(direction, userId, type, json)` for raw wire-level logging (called
  directly from `ChessWebSocketServer.onMessage` and `PlayerSession.send`).

### Full lifecycle
connect (`onOpen`) → `LOGIN` (auto-register or verify, `LOGIN_RESULT`) → **Play** (`PLAY_REQUEST` →
matchmaking queue → ELO-range pairing → `createRoomForMatch` → `MATCH_FOUND` both sides) *or*
**Room** (`ROOM_CREATE`/`ROOM_JOIN` → session starts on 2nd join) → `GameSession` loads
`assets/board.csv`, starts the 50ms tick loop, publishes `GameStartedEvent` → clients send
`SELECT`/`MOVE`/`JUMP`, all funneled through `RealTimeArbiter`/`RuleEngine` inside the shared
`GameManager` → every tick, personalized `STATE_UPDATE`s stream out plus `GAME_EVENT`s for
sound/animation → game ends via checkmate (king captured, detected in `tick()`), `RESIGN`, or the 20s
disconnect timeout → `finishGame` computes/persists Elo, publishes `GameEndedEvent`, broadcasts
`GAME_OVER`.

## 7. Shared protocol & event bus (`shared`)

### Wire format
Gson, no custom adapters. `shared/protocol/MessageCodec` is the single (de)serialization chokepoint:
`encode(type, payload)` serializes `payload` to a JSON **string**, wraps it in a
`Message{type, payloadJson}` envelope, and serializes *that*. So the wire format is an outer JSON object
whose `payloadJson` field is itself a JSON string — a deliberate "envelope holds raw payload JSON"
design so the dispatcher can decode the envelope before knowing the payload's concrete class.
`decodeEnvelope(json) → Message`, `decodePayload(message, Class<T>) → T`.

### `MessageType` (closed enum)
Client→server: `LOGIN, PLAY_REQUEST, PLAY_CANCEL, ROOM_CREATE, ROOM_JOIN, ROOM_CANCEL, SELECT, MOVE, JUMP, RESIGN, PING`.
Server→client: `LOGIN_RESULT, PLAY_WAITING, MATCH_FOUND, PLAY_NOT_FOUND, ROOM_CREATED, ROOM_JOINED, STATE_UPDATE, GAME_EVENT, DISCONNECT_COUNTDOWN, GAME_OVER, ERROR, PONG`.

### Payload classes (`shared/protocol/payload`)
| Type | Payload class | Fields |
|---|---|---|
| LOGIN | `LoginPayload` | username, password |
| LOGIN_RESULT | `LoginResultPayload` | success, userId, username, elo, errorMessage |
| PLAY_REQUEST / PLAY_CANCEL / ROOM_CANCEL / RESIGN / PING / PLAY_WAITING | `EmptyPayload` | marker singleton |
| MATCH_FOUND | `MatchFoundPayload` | roomId, assignedColor, opponentUsername, opponentElo |
| PLAY_NOT_FOUND / ERROR | `ErrorPayload` | message |
| ROOM_CREATE / ROOM_JOIN | `RoomActionPayload` | roomId — for ROOM_JOIN, the code to join (required); for ROOM_CREATE, an optional custom room ID (blank/null = server auto-generates, added 2026-07-22) |
| ROOM_CREATED / ROOM_JOINED | `RoomStatusPayload` | roomId, role |
| SELECT | `SelectPayload` | selected, row, col |
| MOVE | `MoveCommandPayload` | fromRow, fromCol, toRow, toCol — plain structured JSON (changed 2026-07-22; used to be a single packed string like `"WQe2e5"` decoded by a now-deleted `shared.protocol.AlgebraicNotation`) |
| JUMP | `JumpCommandPayload` | row, col |
| STATE_UPDATE | `StateUpdatePayload` | roomId, yourColor, boardRows, boardCols, `view.GameSnapshot snapshot` (a client-view type serialized directly over the wire), whiteUsername, blackUsername |
| GAME_EVENT | `GameEventPayload` | kind (GAME_STARTED/GAME_ENDED/MOVE_EXECUTED/PIECE_CAPTURED/PIECE_JUMPED), color, pieceType, capturedType, message, scoreWhite, scoreBlack, logEntry — also `implements shared.eventbus.Event` so it can be republished directly onto the client's local bus |
| DISCONNECT_COUNTDOWN | `CountdownPayload` | roomId, disconnectedColor, secondsRemaining |
| GAME_OVER | `GameOverPayload` | roomId, winnerColor, reason, white/blackEloDelta, white/blackEloNew, whiteUsername, blackUsername |

**Note on `GameEventPayload`/`StateUpdatePayload`/`GameOverPayload` usernames (added 2026-07-20):**
`GameWindow` used to be built with hardcoded `"White"`/`"Black"` labels, and the win banner/dialog showed
the raw color string instead of a name. Fixed by threading `whiteUsername`/`blackUsername` through
`StateUpdatePayload` (cached in `NetworkGameController`, read when `Main.launchGameWindow` constructs the
window) and through `GameOverPayload` (read directly in `Main.describeGameOver`). `GameEventPayload`'s
`scoreWhite`/`scoreBlack`/`logEntry` fields (only meaningful for `MOVE_EXECUTED`/`PIECE_CAPTURED`) exist
so `client.ui.ScoreboardSubscriber` can update the score labels and move-log table from bus events
instead of the old per-tick snapshot-polling approach — see the bus note below.

**`AlgebraicNotation`** — encodes/decodes `"<COLOR><PIECE><fromSquare><toSquare>"`, e.g. `WQe2e5`.
Rank counting depends on `boardRows`, so conversions need board height to invert correctly.

### Event bus
`shared/eventbus`: `Event` (marker interface), `EventBus` (`ConcurrentHashMap<Class<?>,
CopyOnWriteArrayList<Subscriber<?>>>`, thread-safe `subscribe`/`unsubscribe`/`publish`),
`Subscriber<T>` (`@FunctionalInterface`).

**Two independent bus instances exist** — one server-side (in `ServerMain`, shared by
`GameManager`/`GameEventBroadcaster`/`ServerActivityLogger`), one client-side (`localEventBus` in
`Main.main`). Domain events (`shared/eventbus/events`: `GameStartedEvent`, `GameEndedEvent`,
`MoveExecutedEvent`, `PieceJumpedEvent`) are published **only server-side** by `GameManager`, consumed
server-side by `ServerActivityLogger` and `GameEventBroadcaster`. The client never sees these classes
directly — `NetworkGameController` decodes incoming `GAME_EVENT` messages to `GameEventPayload` and
publishes *that* onto the client's local bus, which is what
`SoundSubscriber`/`AnimationSubscriber`/`ScoreboardSubscriber` listen for. The bus decouples
`GameManager` from logging/broadcasting server-side, and network deserialization from sound/animation
client-side.

**All four of the spec's required bus consumers are now actually bus-driven (fixed 2026-07-20).**
Previously only sound and animation went through the bus — score and move-log updates were done by
`GameLoop` directly polling the snapshot every tick and pushing into `GameWindow.updateSidePanel`,
bypassing the bus entirely. Fixed by adding `client.ui.ScoreboardSubscriber`, which updates the score
labels and appends move-log rows in reaction to `MOVE_EXECUTED`/`PIECE_CAPTURED` bus events (using the
`scoreWhite`/`scoreBlack`/`logEntry` fields on `GameEventPayload`), the same way `SoundSubscriber` and
`AnimationSubscriber` already worked. `GameLoop` no longer touches the side panel at all — it only
renders the board now. `GameWindow.updateSidePanel(snapshot)` still exists but is now called exactly
once, as a one-time backfill in `Main.launchGameWindow`, so a spectator joining mid-game isn't stuck
showing "Score: 0" (bus events only fire going forward, there's no replay for a late joiner).

## 8. `io` package & assets

- **CSV format** (`io/CsvBoardLoader.java`, used by the real server via `GameSession`) — the only
  loader left in `io` now. Reads `assets/board.csv`, comma-separated `<TYPE><COLOR>` tokens (uppercase
  type first, e.g. `RB` = black rook) or `.`. `assets/board.csv` currently holds a standard 8×8 opening
  position. (`io.BoardParser`, an alternate text format used only by the now-deleted console/script
  path, and `io.BoardPrinter`, a non-functional stub, were both deleted 2026-07-21 — see §9.)
- **Assets layout** — one folder per `<type><color>` combo (`assets/BB, BW, KB, KW, NB, NW, PB, PW,
  QB, QW, RB, RW`), each with `states/{idle,jump,long_rest,move,short_rest}/` (matching the 5 states
  the engine models), each state folder with `config.json` (physics/timing/loop metadata) and
  `sprites/1.png`..`5.png`. **`SpriteLoader` ignores `config.json`** — see §9.
- `assets/board.png` — static background board image.

## 9. Known rough edges (resolved items removed 2026-07-21 — see git history if you need the old text)

- **`SpriteLoader` ignores each state's `config.json`** — frame timing (`FRAME_DURATION_MS = 150`,
  `FRAME_COUNT = 5`) is hard-coded in `ImgRenderer`/`SpriteLoader` instead of data-driven from the
  asset metadata. Still true, not touched by the 2026-07-21 cleanup.
- Minor: `shared/protocol/MessageType.java`'s Javadoc references a nonexistent `MessageDispatcherKeys`
  class (real class is `server.dispatch.MessageDispatcher`) — harmless stale comment.
- **No README/run-instructions or working test wiring existed before 2026-07-24** — fixed that day,
  see §13.
- **Manual `javac` + Lombok gotcha** — see §1. Only matters if you're compiling outside Maven/an IDE.
- The following were confirmed dead and **deleted outright** on 2026-07-21 (no longer in the tree, so
  no longer "rough edges" — listed here only so you don't go looking for them):
  `models.GameState`, `realtime.Motion`, `view.ImageView`, `view.JumpHighlight`, `io.BoardPrinter`,
  `controller.GameController`, `controller.ConsoleIO`, `input.InteractionManager`, `io.BoardParser`,
  plus their dedicated tests (`GameStateTest`, `MotionTest`, `BoardPrinterTest`, `ControllerTest`,
  `ConsoleIOTest`, `InteractionManagerTest`) and the fully-commented-out `MainTest`/`ViewTest`, and the
  orphaned `test/resources/scripts/` fixtures. `GameWindow`'s single-arg constructor (a local-play
  name-entry dialog) went with them, since it had no remaining caller.

## 10. Tests (`test/Test/unit`)

51 test files / 339 tests as of 2026-07-25 (see §13/§14 for how these numbers were produced and
verified — `mvn` isn't on the machine this was written on, so it was cross-checked with a hand-rolled
javac + JUnit-Platform-Launcher-+-JaCoCo harness, not just eyeballed). Line coverage: **92.3% on the
in-scope codebase** (1267/1372 lines) per that harness — see §14 for exactly what's in/out of scope
and why, and `pom.xml`'s `jacoco-maven-plugin` `check` execution for the enforced 90% floor.

Covers, roughly by area:
- **Engine/rules/models**: `Board`, `Position`, `Piece` (Lombok `@Value` value objects) ·
  `BoardMapper`, `CoordinateParser` (pixel/tile mapping, all four out-of-bounds edges) · `GameEvent`,
  `MoveEvent`, `JumpEvent`, `RealTimeArbiter` (core engine mechanics, including capture-by-jumper) ·
  `MoveCommand`, `MoveValidation`, `PieceRules`, `RuleEngine` (validation, including the
  unknown-piece-type fallback) · `GameManager` (board init/ownership/busy-state/`colorAt`) ·
  `MoveLogger` (score/notation/history, both colors, all captured-piece values, the no-board fallback
  for rank numbering).
- **Shared protocol/bus**: `MessageCodec` (encode/decode round-trips, every `MessageType`) ·
  `EventBus` (pub/sub, unsubscribe, unrelated-type isolation).
- **Server auth/rating/matchmaking**: `PasswordHasher`/`AuthServiceImpl` (auto-register,
  wrong-password rejection, salted-hash-not-plaintext, against an in-memory fake `UserRepository`) ·
  `SqliteUserRepository` + `SchemaInitializer` (against a real throwaway SQLite file per test,
  not mocked) · `EloRatingService` (K-factor math, underdog/favorite deltas) ·
  `EloRangeMatchCriteria`.
- **Server rooms/dispatch/game**: `RoomIdGenerator`/`Room`/`RoomManager` (role assignment,
  broadcast/sendTo targeting, create/join/custom-ID-collision, active-room tracking) ·
  `MessageDispatcher` (routing, unregistered-type and handler-exception error replies) · every
  `server.dispatch.handlers.*` class (login/reconnect-reattach, play request/cancel + matchmaking
  callbacks, room create/join/cancel, select/move/jump accept-and-reject paths, resign, ping) ·
  `PlayerSession`/`SessionRegistry` (via a `java.lang.reflect.Proxy`-based fake `WebSocket` — the
  real interface has ~25 methods, only `isOpen`/`send` matter here) · `GameEventBroadcaster` ·
  `ServerActivityLogger`/`ClientActivityLogger` · `DisconnectWatchdog` (start/broadcast, cancel,
  already-over short-circuit — see below for what's *not* covered here).
- **io/graphics/view**: `CsvBoardLoader` (including a real load of `assets/board.csv`) · `Image`
  (real `ImageIO`/`Graphics2D` raster ops — load/resize/transparency/compositing/text, all headless-safe,
  no display needed) · `SpriteLoader` (real asset PNGs, caching, frame wraparound) · `ImgRenderer`
  (renders onto an in-memory `BufferedImage` — selection/legal-move/cooldown highlights, pieces,
  captured-piece skipping, zoom scaling, game-over overlay).
- **client bridge/ui**: `NetworkGameController` — both the incoming `onMessage` dispatch (no socket
  needed) *and* the outgoing request methods (`requestPlay`/`handleBoardClick`/etc., which need an
  actually-open socket to exercise since `ServerConnection.send` delegates to `WebSocketClient.send`)
  via a minimal real loopback `WebSocketServer` test double (`TestSupport.LoopbackServer`) — not the
  production server, so this stays independent of SQLite/matchmaking · `LoginController` the same way
  · `LoginConsolePrompt` (via `System.in` redirection) · `SoundSubscriber`.

**Deliberately excluded from the coverage target** (would need GUI automation or a live-socket
production server for comparatively low return — see §14 for the full rationale and the exact
`pom.xml` exclude list): `Main`/`server.ServerMain` (composition roots), `view.GameWindow`,
`client.ui.HomeScreen`, `client.ui.RoomDialog` (Swing UI), `view.GameLoop`,
`client.ui.AnimationSubscriber`/`ScoreboardSubscriber` (all three directly require a real
`GameWindow`), `server.net.ChessWebSocketServer`, `client.net.ServerConnection` (raw socket
transport — the latter is a `final` class, and only its allocation is exercised, never a full
production accept/read loop).

**Known small gaps within the in-scope 92.3%** (each is a handful of lines, individually
low-value/low-risk, left as-is rather than chased for their own sake): a few JDBC/driver
`catch` blocks that are only reachable if the sqlite-jdbc driver or its class were literally
missing from the classpath (`SqliteConnectionProvider`, `SchemaInitializer`) or if UTF-8 somehow
weren't a supported encoding (`PasswordHasher`) — defensive code for conditions that can't happen
in this environment; `DisconnectWatchdog`'s final "countdown reaches zero → auto-resign" branch,
which has no injectable clock and would need a real ~20s wait to exercise via the public API (the
cancel-on-reconnect and already-over-game paths the spec actually cares about *are* covered);
`server.matchmaking.MatchmakingServiceImpl`'s background scan thread and `server.game.GameSession`'s
own move/jump/select/disconnect handling beyond what `RoomManagerTest`/the handler tests already
exercise indirectly — both are covered end-to-end by the live two-client WebSocket smoke test
described in this session's transcript (real server process, real moves, real invalid-move
rejection, real resign→GAME_OVER), just not by a `mvn test`-visible unit test.

`test/Test/integration/` currently has no files. `test/resources/scripts/` no longer exists (it only
fed the now-deleted `ConsoleIO`-driven tests).

## 11. Lombok usage (added 2026-07-21)

See §1 for the dependency and the manual-`javac` build gotcha. Scope, deliberately conservative:
- **`@AllArgsConstructor` only, public fields untouched**: all of `shared/protocol/payload/*` (except
  `EmptyPayload`, a singleton with no fields — left alone) and `shared/eventbus/events/*`, plus
  `view.PieceSnapshot`. Zero call sites changed — code everywhere still reads `payload.username`,
  `event.roomId`, etc. directly. `LoginResultPayload` uses
  `@AllArgsConstructor(access = AccessLevel.PRIVATE)` to preserve its private-constructor +
  `ok(...)`/`fail(...)` static-factory pattern exactly.
- **`@Getter @AllArgsConstructor`**: `controller.MoveCommand`, `view.CooldownHighlight` — both already
  used private-field/hand-written-getter style, so this is a 1:1 boilerplate swap with no API change
  (`getSource()`/`getDestination()`, `getPosition()`/`getRemainingFraction()`/`getType()` all still work
  identically).
- **`@Value`**: `models.Position`, `models.Piece` — full immutable value semantics (private final
  fields, getters, constructor, `equals`/`hashCode`/`toString`). `Position` already had hand-written
  value-equality `equals`/`hashCode`; `Piece` didn't (relied on default identity equality) — verified via
  grep that nothing in the codebase depends on `Piece` identity comparison before adding value equality.
- **Deliberately NOT converted**: `engine.GameEvent` and its subclasses (`MoveEvent`/`JumpEvent`/
  `CooldownEvent`) — these have real behavior beyond data-holding (`execute()`, `getPriority()`) and
  participate in an inheritance hierarchy, where Lombok's `@AllArgsConstructor` doesn't cleanly handle
  `super(...)` chaining. Not worth the risk for the boilerplate saved.

## 12. Course-slide compliance pass (2026-07-22)

The user shared a set of course slides on the server architecture (Event Bus, "the network only passes
messages," Command vs Event, "input is not authorization," "time is part of the game rules," Snapshot
immutability, "disconnection is not immediate loss," server-authoritative move rejection for
wrong-owner/viewer clients) and asked whether the codebase actually matches them. Findings:

**Already compliant, no change needed:**
- Event Bus decoupling `GameManager` from score/sound/history/broadcast — built earlier (§7).
- Network layer as a thin, business-logic-free transport (`ChessWebSocketServer` just decodes and
  dispatches; `MessageDispatcher`/handlers own all the actual logic).
- "Successful parsing is not authorization": `GameSession.handleMove`/`ownsColorAt` and
  `RuleEngine.validateMove` always re-derive the real piece/color/legality from the server's own board
  state — a client's claimed move is only ever "which two squares," never trusted for who/what is
  moving. This was already true even when the wire format was a string; switching to structured JSON
  (below) didn't change this defensive posture, it just removed an unnecessary encode/decode step.
- "Time is part of the game rules": the server (`RealTimeArbiter`/`GameSession.tick()`) is the sole
  clock; the client only ever renders interpolated positions, never advances anything itself.
- Snapshot immutability: `GameSnapshot`'s lists were already wrapped in
  `Collections.unmodifiableList(...)` — a client can read but never mutate the live model through it.
- Viewer/spectator can't move pieces: `ownsColorAt` always fails for a spectator, since
  `GameSession.colorOf` returns `'s'` for them and no board square is ever color `'s'`.

**Gap found and fixed — reconnection.** The slides describe disconnection as *not* an automatic loss:
a 20-second window during which reconnecting cancels the auto-resign timer. The scaffolding for this
existed (`DisconnectWatchdog.cancel()`, `PlayerSession.getActiveWatchdog()`) but **nothing ever called
either one** — confirmed by grepping for callers before touching anything. A disconnect always ran the
full 20s countdown to auto-resign regardless of whether the player came back. Fixed per §6's
"Reconnection" note (`RoomManager.activeRoomByUserId`, `GameSession.watchdogsByUserId`,
`LoginHandler.reattachToActiveGameIfAny`). Verified live with a throwaway WebSocket test harness
(connect → login → disconnect mid-game → observe countdown ticks on the other client → reconnect with
the same credentials → confirm `STATE_UPDATE` resumes and the other client sees the countdown cancel).

**Explicit feature requests handled in the same pass:**
- **Player names at the top of the window** — `GameWindow`'s title bar now reads
  `"Kung Fu Chess - <white> (White) vs <black> (Black)"` instead of the static `"Kung Fu Chess"`.
- **Custom room ID on create** — see §6's `RoomManager.createRoom` note. Client-side, `RoomDialog`'s
  single text field now doubles for both Join (required) and Create (optional); `RoomActionPayload`
  carries it either way.
- **JSON move payload instead of `"WQe2e5"`** — see the §7 payload table. `shared.protocol.AlgebraicNotation`
  is **deleted** (it had exactly two callers, both updated to use plain row/col fields instead) since it
  had no remaining purpose once the wire format changed. `NetworkGameController.findPieceTypeAt` (only
  existed to help build the old notation string) was deleted alongside it.
- **Dedicated login controller** — new `client.bridge.LoginController implements ServerMessageListener`,
  owning exactly the `LOGIN`/`LOGIN_RESULT` round-trip. `Main.connectAndLogin` routes the connection's
  message callback through a one-element array holder so it can point at `LoginController` first, then
  repoint at `NetworkGameController` once login succeeds — `NetworkGameController` no longer has any
  login-handling code at all (`requestLogin`, `onLoginResult`, the `LOGIN_RESULT` switch case, and their
  imports were all removed since they became dead the moment `Main` stopped calling them).

**Build note:** manual `javac` compiles during this pass twice found `target/classes` unexpectedly
emptied out between compile and use (most likely IntelliJ's Maven integration reacting to the `pom.xml`
edits with its own background build/clean — not something this session did deliberately). Compiling to
a separate `build/classes` directory sidestepped it. If you hit "cannot find symbol" errors on classes
you know compiled fine moments earlier, check whether `target/classes` still has content before assuming
the code is broken.

## 13. README, portable run scripts, and test-wiring/coverage pass (2026-07-24)

A review of the repo against a course grading checklist flagged two items as failing: no
README/run-instructions, and self-reported "80% test coverage" that was actually ~13% of classes
having any test at all, with the Maven test wiring itself broken. This pass fixed both.

**README (new `KSChessPlay/README.md`)** — build/run/test instructions that didn't exist before.
Points at this file for architecture.

**`run_client.ps1`/`run_server.ps1` rewritten to be portable.** They previously `Set-Location`d to a
hardcoded absolute path (`c:\Users\User\Desktop\...`) and hand-built a classpath pointing at
`C:\Users\User\.m2\repository\...` — neither works on any machine but the one they were written on,
and the classpath bypassed the Maven shaded-jar build entirely. Both scripts now delegate to a shared
`build-and-run.ps1` (`Invoke-KungFuChess`), which:
- locates the project via `$PSScriptRoot` (never a hardcoded path);
- prefers a real `mvn package -DskipTests` and runs the resulting `target/kungfu-chess-{client,server}.jar`
  shaded jar, so Maven stays the actual source of truth for the build;
  falls back to `javac` (with the Lombok annotation-processor flags from §1) against jars resolved
  from `$env:USERPROFILE\.m2\repository` only if `mvn` isn't on `PATH` — the situation the old scripts
  were apparently written for. That fallback classpath must include `slf4j-api` (sqlite-jdbc's JDBC
  driver class-inits against it, `NoClassDefFoundError` otherwise) — a real `mvn` build gets this
  transitively for free from sqlite-jdbc's POM, so it's easy to miss when hand-rolling a classpath;
  the original scripts' inclusion of `slf4j-api-2.0.6.jar` was the only clue this was needed.

**`pom.xml`: `testSourceDirectory` was never set**, so `mvn test` looked in the (nonexistent, for this
project) default `src/test/java` and silently ran zero tests — the 14 tests that existed only ever ran
via IntelliJ `.idea/` run configurations. Fixed with `<testSourceDirectory>test</testSourceDirectory>`,
which (given how `test/Test/unit/*.java` is laid out on disk) required renaming every test file's
`package test.Test.unit;` to `package Test.unit;` to match Maven's folder-must-match-package
expectation — a one-line mechanical edit per file, no test logic touched. Also added the
`jacoco-maven-plugin` (wasn't present at all) so `mvn test` now produces `target/site/jacoco/index.html`
— there was previously no tool that could even measure coverage, so "80%" wasn't a number this project
could produce.

**`BoardMapperTest` was dead** — entirely commented out, because it called `BoardMapper.mapStringToPosition(...)`,
a method that no longer exists on the class (current `BoardMapper` only has `toPixelX`/`toPixelY`,
console-notation-era API having been removed along with the rest of the local-play stack, §9). Rewritten
against the current API.

**Found and fixed a real bug in `RealTimeArbiterTest.testCooldownAfterMoveKeepsPieceBusy`** while
getting the suite to actually run and pass (it doesn't touch production code): the test constructed
its own standalone `RealTimeArbiter` alongside `GameManager`'s internal one and shared only the
`Board` between them. `MoveEvent.execute()` calls back into `gameManager.registerLongRestCooldown(...)`,
which registers the post-move cooldown on **`GameManager`'s own arbiter** — a different instance from
the test's — so the test then asserted busy-state on an arbiter that never received the cooldown event
and incorrectly saw `false`. Fixed by driving the test through `GameManager`'s public API
(`requestMove`/`handleWait`/`isPieceBusy`) instead, which is also what production `GameSession` actually
does — no other test in the file hit this because none of them asserted `isPieceBusy` on the
*destination* square right after a cooldown-chaining move landed.

**Test coverage: 14 → 27 files, ~131 → 222 tests**, adding coverage to packages that had none —
`shared.protocol`, `shared.eventbus`, `server.auth`, `server.rating`, `server.matchmaking`,
`server.rooms`, `io`, and the `client.bridge` incoming-message path. Full breakdown in §10, including
what was deliberately left uncovered and why (Swing UI, raw WebSocket transport, background scan/tick
threads — real-infra-heavy, comparatively low return for a unit suite). No mocking library is in the
project (`junit-jupiter` only); new tests follow the existing convention of hand-written in-memory
fakes for interfaces (`UserRepository`, `MatchListener`, `MessageSink`) and real-but-throwaway
instances for concrete things (a real SQLite file per test via `@TempDir`, a real never-connected
`ServerConnection` for `NetworkGameController`, a real `GameSession` resigned in `@AfterEach` to avoid
leaking its ticking thread).

**Verification note:** `mvn` is not on `PATH` in the environment this pass was done in (confirmed by
trying it — `mvn : The term 'mvn' is not recognized...`), so none of the above was verified via an
actual `mvn test` run. Instead, everything was cross-checked with a hand-rolled `javac` + JUnit-Platform-
Launcher harness (compiles `src/` and `test/` against the same dependency jars `pom.xml` pins, runs via
`org.junit.platform.launcher`) reproducing what `mvn test` should do given the `testSourceDirectory` fix
above. All 222 tests pass under that harness. **Run an actual `mvn test` at least once to confirm the
Maven-side wiring behaves identically** — the harness proves the tests and fix are correct, not that
`pom.xml`'s `testSourceDirectory`/surefire/JaCoCo config is 100% right, since surefire's own test
discovery/execution was never exercised directly.

## 14. Pushing to ≥90% line coverage (2026-07-25)

Follow-up to §13: asked to raise coverage to at least 90%. A literal 90% across the *entire*
codebase — including Swing UI and `main()` entry points — would need GUI-automation tooling
(e.g. `assertj-swing`) and a new test dependency, which is a fundamentally different scope than
"write more unit tests." Agreed scope with the user first: **exclude Swing UI, raw WebSocket
transport, and composition-root entry points from the target**, hit ≥90% line coverage on
everything else with plain JUnit (no new dependencies) — the standard way most real Java projects
handle UI/bootstrap code in a coverage gate. The exact exclude list (mirrored in both the
measurement harness and `pom.xml`'s `jacoco-maven-plugin`, see below):
`Main`, `server.ServerMain` (composition roots — nothing to unit-test, they only wire other already-
tested classes together), `view.GameWindow`, `client.ui.HomeScreen`, `client.ui.RoomDialog` (Swing
`JFrame`/dialogs), `view.GameLoop`, `client.ui.AnimationSubscriber`, `client.ui.ScoreboardSubscriber`
(all three take a real `GameWindow` and call its methods directly — no interface to fake against),
`server.net.ChessWebSocketServer`, `client.net.ServerConnection` (raw socket transport — the latter
is `final` and extends `WebSocketClient`; only *constructing* one is safe without a live socket, a
full accept/read loop needs a real server).

**How coverage was actually measured** (`mvn` still isn't on `PATH` here — see §13): JaCoCo ships
an instrumenting Java agent (`org.jacoco.agent-*-runtime.jar`) and a core analysis library
(`org.jacoco.core`/`org.jacoco.report`), both already present in the local `~/.m2/repository`
(pulled in earlier by adding the `jacoco-maven-plugin` to `pom.xml`) — no standalone `jacococli.jar`
was available, so a small throwaway `CoverageReport.java` was written against JaCoCo's Java API
directly (`Analyzer`/`CoverageBuilder`) to print per-class line coverage from a `jacoco.exec` file,
with the same exclude list applied for an "in-scope" subtotal. One real gotcha: the harness's
`javac` calls were not initially passed `--release 17` (matching `pom.xml`'s declared target), so
classes compiled as Java 23 bytecode (class file major version 67) — JaCoCo 0.8.11's bundled ASM
can't read that far ahead and threw `Unsupported class file major version 67` on every class. Fixed
by adding `--release 17` to the harness's `javac` invocations; this doesn't affect the real `mvn`
build, which already compiles to whatever `maven.compiler.target` says.

**Where the ~90 lines needed to close the gap came from**, roughly in the order they were added
(baseline was 58.5% in-scope after §13's work):
- **Handler-level tests for all of `server.dispatch.handlers.*`, `MessageDispatcher`, `PlayerSession`,
  `SessionRegistry`** — the single largest gap (~180 lines). `PlayerSession`/`SessionRegistry` needed
  a `WebSocket` test double; rather than hand-implement the real interface's ~25 methods, a
  `java.lang.reflect.Proxy`-based fake (`TestSupport.fakeWebSocket`) handles `isOpen`/`send` and
  defaults everything else, with `hashCode`/`equals`/`toString` specifically special-cased to
  identity semantics once `SessionRegistryTest` revealed `ConcurrentHashMap` calls `hashCode()` on
  the proxy and a naive default (`null`) NPEs trying to unbox to `int`.
- **`GameEventBroadcaster`, `ServerActivityLogger`, `DisconnectWatchdog`** — real `EventBus` +
  real `RoomManager`/`Room` (a fake `MessageSink` records what gets broadcast) rather than mocking
  the domain events. `DisconnectWatchdog` ticks a real 1-second-interval `ScheduledExecutorService`
  with no injectable clock, so its tests use short real-time waits (~1.2s) for start/cancel/already-
  over behavior, deliberately *not* waiting the full real 20s for the "countdown reaches zero" branch
  (see §10's "known small gaps").
- **`graphics.Image`, `view.ImgRenderer`, `view.SpriteLoader`** (~150 lines, previously assumed to
  need a display and skipped) — on reflection these are pure `ImageIO`/`Graphics2D`/`BufferedImage`
  raster operations with no `JFrame`/window involved anywhere, so they're just as headless-safe as
  any other logic class. This was the single best-value correction in this pass: real coverage on
  actual "Graphics" checklist behavior (transparency, compositing, sprite caching, cooldown/highlight
  rendering) instead of leaving it excluded alongside the *actually*-Swing-coupled classes.
- **`client.bridge.NetworkGameController`'s outgoing methods and `client.bridge.LoginController`**
  (~40 lines) — both need an actually-open socket (`ServerConnection.send` delegates to
  `WebSocketClient.send`, which throws `NotYetConnectedException` otherwise). Rather than exclude
  them alongside `ServerConnection` itself, `TestSupport.LoopbackServer` (a minimal real
  `org.java_websocket.server.WebSocketServer` bound to an ephemeral port) gives them a real, cheap,
  production-independent socket to talk to. One real bug surfaced and fixed here: an early version of
  `NetworkGameControllerOutgoingTest` re-read the *last* received message after every send, which
  raced the network on the "click same square twice → deselect" test (the second message hadn't
  arrived yet when the assertion ran, so it silently re-checked the first message) — fixed by
  tracking how many messages had already been consumed and awaiting that exact count before reading.
- **Misc edge cases** (~30 lines) — `LoginConsolePrompt` (via `System.in` redirection),
  `SoundSubscriber`, `ClientActivityLogger`, and small previously-missed branches in `MoveLogger`
  (black-side captures, every captured-piece-type value, the no-`Board` rank-numbering fallback),
  `GameManager.colorAt`, `Room.getUser(long)`, `RuleEngine`'s unknown-piece-type fallback, and all
  four `CoordinateParser` out-of-bounds edges (a genuine gotcha found here: Java integer division
  truncates toward zero, so `-10 / 100 == 0`, *not* `-1` — a click at x=-10 still lands in column 0;
  the test needed x=-150 to actually produce a negative column and exercise that branch).

**Result: 27 → 51 test files, 222 → 339 tests, all passing** (verified with the harness above).
In-scope line coverage: **92.3%** (1267/1372 lines), comfortably over the 90% target. `pom.xml` now
enforces this durably — the `jacoco-maven-plugin`'s new `check` execution (bound to the `test`
phase, same exclude list as `report`) fails the build if in-scope `LINE` `COVEREDRATIO` drops below
`0.90`, so this isn't just a one-time measurement that can silently regress. As with §13: **run an
actual `mvn test` to confirm `jacoco:check` passes for real** — it was designed against the same
exclude patterns and rule shape JaCoCo's Maven docs specify, but never executed through Maven itself
in this environment.

## 15. Server split into 6 processes + Redis/Postgres (2026-07-30) — current process map

The single-process server described in §6 is gone. `PLAN_split_6_services.md`'s sub-steps 3.1-3.8 are
now all done (3.1/3.2 landed earlier - commit `10c2410 "starting docker"`; 3.3-3.8 and
`Server_Design.md` steps 4-5 landed in this pass), each `*Main`/`*Runtime` pair following the same
shape: a thin composition root (not unit tested — connects to a real NATS/Redis/Postgres, see
`pom.xml`'s jacoco excludes) wrapping a domain class that *is* unit tested against
`shared.bus.InMemoryMessageBus` (and, where relevant, an in-memory double for Redis/Postgres). All
inter-process traffic goes over NATS (`shared.bus.MessageBus`/`NatsMessageBus`), subjects defined in
`shared.bus.RoomBusSubjects` — see `PLAN_split_6_services.md` §2 for the full subject table and §3/§6
for the sub-step-by-sub-step build narrative (including *why* each design choice was made, not just
what it is).

| Process | Entry point | Owns | Touches a database? |
|---|---|---|---|
| Game Server Shard | `server.shard.GameServerShardMain` | Runs `GameSession`s (the actual game engine) — one per allocated room. Publishes state/events to `room.<roomId>.updates`. Publishes Elo updates to NATS (`server.persistence.BusEloWriter`) instead of writing them itself. | No |
| WS Gateway | `server.gateway.WsGatewayMain` | Every client WebSocket connection (`ChessWebSocketServer`/`SessionRegistry`/dispatch handlers). Sends IDENTIFY-derived identity, never a password (see below) - login itself lives in the API Gateway now. Relays gameplay traffic to/from the Shard over `room.<roomId>.commands`/`.updates`. | No |
| Matchmaker | `server.matchmaking.MatchmakerMain` | The ELO-range pairing queue (`MatchmakingServiceImpl`, algorithm unchanged since before any split; its waiting-queue *data* now lives in Redis - `RedisWaitingPlayersQueue`). Reaches the Allocator only over the bus (`BusRoomAllocationClient`), never in-process. | Redis |
| Game Allocator | `server.allocator.GameAllocatorMain` | Room creation/joining/cancellation and the active-room/reconnect registry (`RoomManager`, unchanged core logic; the registry itself now lives in Redis - `RedisActiveRoomRegistry`). Picks which Shard instance runs each room via round-robin (`ShardSelector`, `SHARD_IDS` env var). | Redis |
| API Gateway | `server.apigateway.ApiGatewayMain` | The *only* `AuthService`/`UserRepository` in the whole system now. Exposes `POST /login` over plain `com.sun.net.httpserver` (not NATS - the one REST call in this project) and `/health`. | SQLite or Postgres (`PersistenceConfig`) |
| Elo writer | `server.persistence.EloWriterMain` | Consumes `BusEloWriter`'s NATS messages, buffers them, and drains a batch into storage every ~2s (`EloWriterRuntime`) instead of the Shard writing synchronously per game. | SQLite or Postgres (`PersistenceConfig`) |

**There is no "the server" process anymore.** `server.ServerController` (formerly `ServerMain`),
`server.config.GameServicesConfig`, and `server.rpc.MonolithBusGateway` were deleted outright once
3.3/3.4 left them with zero responsibilities — `RoomManager`/`MatchmakingService` moved to their own
processes, and the monolith's `AuthService` copy was already dead weight (the Gateway ran its own
from §3.2 until §3.5 retired that too, in favor of the API Gateway above).

**Login moved to REST (§3.5).** `MessageType.LOGIN`/`LoginHandler` are gone. The Swing client now
calls `POST /login` on the API Gateway (`client.net.ApiGatewayClient`) *before* opening the WebSocket
at all; once that succeeds, it opens the socket and sends `IDENTIFY` (`IdentifyPayload` - just the
already-authenticated userId/username/elo, no password) to attach that identity to the connection
(`server.dispatch.handlers.IdentifyHandler`, no `AuthService` dependency at all). `LOGIN_RESULT` is
reused as the ack for both the REST call and the socket IDENTIFY, so the wire shape clients already
know didn't need to change.

**Observability (§3.6).** Every process's `ServerActivityLogger` now also emits one JSON object per
line to stdout (alongside the existing human-readable file line) - see its own javadoc. Every process
exposes a trivial `/health` (`shared.health.HealthServer`, `HEALTH_PORT`, default `8081`; the API
Gateway folds it onto its existing HTTP server/`PORT` instead). Real dashboards/metrics are still
explicitly out of scope, per the plan's own framing.

**Build/deploy (§3.7/§3.8).** One root `Dockerfile`, build-arg `JAR_CLASSIFIER`, replaces what used to
be 4 near-duplicate per-process Dockerfiles. `docker-compose.yml` runs all 6 processes above plus
`nats`/`redis`/`postgres`. `build-and-run.ps1`/`run_server.ps1` are marked deprecated in favor of
`docker compose up --build` (there's no single process left for `run_server.ps1` to launch);
`run_client.ps1` still works for running the Swing client locally against a Dockerized backend.

**Multi-shard routing, in brief**: `room.allocated` stays one global (broadcast) subject rather than
gaining a new per-shard one — `RoomAllocatedPayload` just carries a `shardId` field, and each
`GameShardRuntime` ignores allocations whose `shardId` isn't its own. `docker-compose.yml` today still
only runs one `shard` replica (`SHARD_ID=shard-1`, matching the Allocator's single-entry
`SHARD_IDS=shard-1`) — scaling to more is adding entries to both.

**Redis (Server_Design.md step 4), in brief**: two pieces of state moved out of process-local memory
into Redis, both accessed through a small interface with an `InMemory*` test double and a `Redis*`
production implementation (`shared.redis.RedisConnectionProvider` underneath both) - exactly the
`MessageBus`/`NatsMessageBus`/`InMemoryMessageBus` pattern applied again. **Not** moved to Redis:
`RoomManager`'s room/role-reservation state (`Room`, the `rooms` map) - its atomicity today comes from
plain Java `synchronized`/`ConcurrentHashMap.putIfAbsent`, and reproducing that correctly over Redis
would need real transactions/Lua scripting; that's deliberately left as a known limitation rather than
risking a correctness regression in already-tested code for a one-line plan bullet. Also **not**
solved: true multi-replica Matchmaker dispatch - see `WaitingPlayersQueue`'s javadoc for exactly why
that's still fine for today's single-replica reality but not fully distributed yet.

**Postgres + async persistence (Server_Design.md step 5), in brief**: `UserRepository` gained a
`default updateEloBatch(Map<Long,Integer>)` method (loops `updateElo` - correct for every existing
implementation/test double unchanged) that `PostgresUserRepository` overrides with one real batched
transaction. `server.game.GameSession` depends on the narrower `server.rating.EloWriter` (a single
`updateElo` method) instead of the full `UserRepository` it used to need just for this one call - the
Shard's production wiring passes a `BusEloWriter` (publishes to NATS) instead of a real repository, so
the Shard no longer touches a database *at all*. `EloWriterMain` is the one consumer.

**Cross-references for a fresh session**: `PLAN_split_6_services.md` is the design doc this table
summarizes — read it for the *why*, not just the *what*, including §6's own running status log.
`Server_Design.md` is one level up (the overall 6-service target architecture and production capacity
plan this whole split is working towards) - steps 6-10 there (K3s, autoscaling, load test, multi-
region, Agones) are not started.
