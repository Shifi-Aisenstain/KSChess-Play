# KungFu Chess — Architecture Reference

Standalone architecture summary for AI assistants. Read this before exploring the codebase — it should
cover enough for architecture questions, bug hunting, and feature work without re-reading every file.
Verify specifics with Read/Grep before relying on them for anything destructive (renamed/moved code,
line numbers) — this doc can drift from the code over time.

**Written:** 2026-07-19, based on the working tree at that time (mid-refactor from single-player to
networked client/server — see §9).

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

## 1. Tech stack & key dependencies (`pom.xml`)

- `org.java-websocket:Java-WebSocket:1.5.4` — WebSocket transport (both client and server side).
- `com.google.code.gson:gson:2.10.1` — JSON (de)serialization for the wire protocol. Plain
  `new GsonBuilder().create()`, no custom adapters/naming policy.
- `org.xerial:sqlite-jdbc:3.44.1.0` — user accounts / auth / Elo persistence.
- `org.junit.jupiter:junit-jupiter:5.10.1` — test scope only.
- `maven-shade-plugin` builds two fat jars at `package`: classifier `client` (main class `Main`) and
  classifier `server` (main class `server.ServerMain`).

## 2. Package map

| Package | Responsibility |
|---|---|
| `models` | Pure domain data: `Position`, `Piece`, `Board`. (`GameState` is dead — see §9.) |
| `controller` | Local/offline click-driven play: `GameController`, `ConsoleIO`, `MoveCommand`. Not used by the networked client. |
| `engine` | The real-time simulation core: `GameManager` (orchestrator), `GameEvent`/`MoveEvent`/`JumpEvent`/`CooldownEvent`, `MoveLogger`. |
| `realtime` | `RealTimeArbiter` (the concurrency/cooldown scheduler) and `Motion` (dead — see §9). |
| `rules` | Stateless move validation: `RuleEngine`, `PieceRules`, `MoveValidation`. No board mutation. |
| `graphics` | `Image` — JDK `ImageIO`/`Graphics2D` wrapper (sprite loading, transparency, drawing). |
| `input` | Click→board-coordinate mapping: `CoordinateParser`, `BoardMapper`, `InteractionManager` (local mode only). |
| `io` | Board loading/printing: `BoardParser` (console script format), `CsvBoardLoader` (real game format, `assets/board.csv`), `BoardPrinter` (stub, dead). |
| `view` | Rendering only, no simulation: `GameWindow`, `GameLoop`, `ImgRenderer`, `SpriteLoader`, `GameSnapshot`/`PieceSnapshot`/`SnapshotSource`, `CooldownHighlight`. (`ImageView`, `JumpHighlight` are dead — see §9.) |
| `client` | Networked client composition: `client.net` (`ServerConnection`, `ServerMessageListener`), `client.bridge` (`NetworkGameController`), `client.ui` (`HomeScreen`, `RoomDialog`, `LoginConsolePrompt`, `AnimationSubscriber`), `client.audio` (`SoundSubscriber`), `client.logging` (`ClientActivityLogger`). |
| `server` | Everything server-side — see §5 for the full subpackage breakdown. |
| `shared` | The wire contract and pub/sub bus used by both sides: `shared.protocol` (+`payload`), `shared.eventbus` (+`events`). |
| `Main` (default package) | Client composition root / entry point. |

## 3. Core domain model (`models`)

- **`Position`** — immutable `{int row, int col}`, value equality. No bounds validation of its own.
- **`Piece`** — immutable `{char color ('w'/'b'), char type ('R','K','P','Q','B','N')}`. No identity/ID:
  two same-color-and-type pieces are indistinguishable by value. Engine code tracks pieces by board
  position, not piece identity.
- **`Board`** — wraps `Piece[rows][cols]`. `getPieceAt`/`setPieceAt` are bounds-checked;
  **`setPieceAt` on an out-of-bounds `Position` silently no-ops** (used deliberately, e.g. by
  `GameManager.clearPosition`). `getReadOnlyMatrixView()` returns `String[][]` (`"<color><type>"` or
  `"."`) for console printing/tests.
- **`GameState`** — **dead code**: models turn-based play (`currentTurn`, `switchTurn()`), which
  contradicts the turnless design. Not used by `engine`/`server`/`client`, only by `GameStateTest`.
  Treat as a leftover from an earlier turn-based prototype; do not build on it without confirming.

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

**Who drives the clock:** in networked play, `server.game.GameSession.tick()` calls
`gameManager.handleWait(50)` every `TICK_MS = 50ms` on a dedicated `ScheduledExecutorService` — the
server is the single authoritative clock, ticking 20×/sec per room. In local/console mode,
`controller.ConsoleIO` reads `wait <ms>` lines directly from a script and calls the same method.

### Example move trace (networked)
1. **Client** — two clicks: `GameWindow` → click handler in `Main.launchGameWindow` →
   `client.bridge.NetworkGameController.handleBoardClick`. First click sets `selectedPosition`, sends
   `SELECT` (server-side highlight only). Second click builds an algebraic string via
   `shared.protocol.AlgebraicNotation.format(...)` (e.g. `"WQe2e5"`) and sends `MOVE`
   (`MoveCommandPayload{command}`).
2. **Server dispatch** — `ChessWebSocketServer.onMessage` → `MessageDispatcher.dispatch` →
   `server.dispatch.handlers.MoveHandler.handle`: requires a logged-in user + active `GameSession`
   (via `HandlerSupport`), parses the string back via `AlgebraicNotation.parse`, calls
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
username/password) → opens `client.net.ServerConnection` → sends `LOGIN`, awaits `LOGIN_RESULT`
(`CompletableFuture`, 10s timeout) → on success, builds `client.ui.HomeScreen` wired to a
`HomeScreenListener` that forwards Play/Room actions into `client.bridge.NetworkGameController` → on
the controller's first `STATE_UPDATE` (`setOnFirstStateUpdate`), hides the home screen and calls
`launchGameWindow`: builds `BoardMapper` + `SpriteLoader` + `ImgRenderer`, a `GameWindow`, wires mouse
clicks through `CoordinateParser.parseClick` → `controller.handleBoardClick`/`handleBoardRightClick`,
wires disconnect-countdown/game-over callbacks, subscribes an `AnimationSubscriber` to a local
`EventBus`, and starts a `view.GameLoop(controller, renderer, window, 60)`.

**Local/offline single-player has no live entry point today.** `controller.GameController`,
`controller.ConsoleIO`, `input.InteractionManager` (the local, `GameManager`-owning stack) are only
reachable via unit tests and console-script fixtures (`test/resources/scripts/`) — `Main.java`
unconditionally requires a server connection. Don't assume local play works without checking `Main.java`
first.

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
- **`view/GameWindow`** — Swing `JFrame`: board canvas (`JLabel` + `ImageIcon`), mouse-wheel zoom
  (0.5–2.0×), left/right move-history `JTable`s, a banner label (start/end/capture flash messages,
  auto-hides after 2.2s), a countdown label (disconnect auto-resign countdown), score panel + "New
  Game" button. Two constructors: `GameWindow(Image)` (legacy local-play name-entry dialog) vs.
  `GameWindow(Image, whiteName, blackName)` (networked path, used by `Main`).

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
- **`PlayerSession implements MessageSink`** — per-socket state (`User`, `currentRoomId`,
  `activeWatchdog`); `send` guards on `connection.isOpen()`.
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
  ended.

### Rooms (`server/rooms`)
- **`Room`** — `sinks`/`roles` (`WHITE|BLACK|SPECTATOR`)/`users` maps + the attached `GameSession`.
  `reserveRole` assigns WHITE → BLACK → SPECTATOR in join order, idempotently.
- **`RoomManager`** — `createRoom` (manual room flow, no session until 2nd join),
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
| ROOM_CREATE / ROOM_JOIN | `RoomActionPayload` | roomId |
| ROOM_CREATED / ROOM_JOINED | `RoomStatusPayload` | roomId, role |
| SELECT | `SelectPayload` | selected, row, col |
| MOVE | `MoveCommandPayload` | command (e.g. `"WQe2e5"`) |
| JUMP | `JumpCommandPayload` | row, col |
| STATE_UPDATE | `StateUpdatePayload` | roomId, yourColor, boardRows, boardCols, `view.GameSnapshot snapshot` (a client-view type serialized directly over the wire) |
| GAME_EVENT | `GameEventPayload` | kind (GAME_STARTED/GAME_ENDED/MOVE_EXECUTED/PIECE_CAPTURED/PIECE_JUMPED), color, pieceType, capturedType, message — also `implements shared.eventbus.Event` so it can be republished directly onto the client's local bus |
| DISCONNECT_COUNTDOWN | `CountdownPayload` | roomId, disconnectedColor, secondsRemaining |
| GAME_OVER | `GameOverPayload` | roomId, winnerColor, reason, white/blackEloDelta, white/blackEloNew |

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
publishes *that* onto the client's local bus, which is what `SoundSubscriber`/`AnimationSubscriber`
listen for. The bus decouples `GameManager` from logging/broadcasting server-side, and network
deserialization from sound/animation client-side.

## 8. `io` package & assets

- **Console/script format** (`io/BoardParser.java`) — `Board:` header + rows of
  `<color><type>` tokens (lowercase color, e.g. `wR`) or `.`. Used by `ConsoleIO` and test script
  fixtures only.
- **CSV format** (`io/CsvBoardLoader.java`, used by the real server via `GameSession`) —
  `assets/board.csv`, comma-separated `<TYPE><COLOR>` tokens (uppercase type first, e.g. `RB` = black
  rook) or `.`. **Note the token order/case is the opposite of `BoardParser`'s** — kept as two separate
  classes deliberately (per the class's own doc comment). `assets/board.csv` currently holds a standard
  8×8 opening position.
- **`io/BoardPrinter.java`** is a non-functional stub (see §9) — not the real console-printing path
  (that logic lives inline in `ConsoleIO.printBoardToConsole`).
- **Assets layout** — one folder per `<type><color>` combo (`assets/BB, BW, KB, KW, NB, NW, PB, PW,
  QB, QW, RB, RW`), each with `states/{idle,jump,long_rest,move,short_rest}/` (matching the 5 states
  the engine models), each state folder with `config.json` (physics/timing/loop metadata) and
  `sprites/1.png`..`5.png`. **`SpriteLoader` ignores `config.json`** — see §9.
- `assets/board.png` — static background board image.

## 9. Known rough edges / dead code (check before assuming these are load-bearing)

- **`models/GameState.java`** — turn-based, contradicts the turnless design. Unused outside
  `GameStateTest`. Don't build on it.
- **`realtime/Motion.java`** — an earlier float-seconds animation model, superseded by
  `MoveEvent` + `GameSnapshot` interpolation. Unused outside `MotionTest`.
- **`view/ImageView.java`** — empty stub (`public class ImageView {}`), superseded by `graphics.Image`.
- **`view/JumpHighlight.java`** — complete but unreferenced; superseded by `CooldownHighlight.Type.JUMP`.
- **`io/BoardPrinter.java`** — non-functional stub; `BoardPrinterTest` actually tests `Board`, not this class.
- **`test/Test/unit/MainTest.java`** and **`ViewTest.java`** — fully commented out, reference stale
  APIs (`Main.main` console mode; a `view.Renderer` class that no longer exists — replaced by
  `ImgRenderer`).
- **Local/offline single-player has no live entry point** from `Main.java` — only reachable via unit
  tests / script fixtures. Verify against `Main.java` before claiming "the client supports local play."
- **`SpriteLoader` ignores each state's `config.json`** — frame timing (`FRAME_DURATION_MS = 150`,
  `FRAME_COUNT = 5`) is hard-coded in `ImgRenderer`/`SpriteLoader` instead of data-driven from the
  asset metadata.
- **Maven test-source wiring is unresolved**: tests live in `test/Test/unit`, not Maven's default
  `src/test/java`, and `pom.xml` has no `testSourceDirectory` override. It's unclear whether `mvn test`
  currently runs any of the 21 unit tests, versus them only running via IDE (`.idea/`) run
  configurations. **Verify with an actual `mvn test` run before asserting current test coverage.**
- **No `TODO`/`FIXME`/`XXX` comments exist anywhere in `src/`** (confirmed via grep) — rough edges are
  structural/dead-code, not inline-flagged.
- Minor: `shared/protocol/MessageType.java`'s Javadoc references a nonexistent `MessageDispatcherKeys`
  class (real class is `server.dispatch.MessageDispatcher`) — harmless stale comment.
- The working tree is mid-refactor from a single-player build into the networked client/server split
  (per recent Hebrew commit messages: UI/graphics-routing issues, jump bugs). Expect rendering/UI code
  in `view/` to be the least stable area.

## 10. Tests (`test/Test/unit`, 21 files)

Covers: `Board`, `Position`, `Piece` (value objects) · `BoardMapper`, `CoordinateParser` (pixel/tile
mapping) · `ConsoleIO` (console script parsing) · `GameController`, `InteractionManager` (local
click-driven play) · `GameEvent`, `MoveEvent`, `JumpEvent`, `RealTimeArbiter` (core engine mechanics,
including capture-by-jumper) · `MoveCommand`, `MoveValidation`, `PieceRules`, `RuleEngine` (validation)
· `GameManager` (board init/ownership/busy-state) · `GameState` (dead code, see §9) · `Motion` (dead
code, see §9). `MainTest`/`ViewTest` are inert (fully commented out, stale APIs).
`test/Test/integration/` currently has no files. `test/resources/scripts/` has 6 console-protocol
fixture files (`01_board_parsing` .. `06_game_over`) consumed by `ConsoleIO`-driven tests.
