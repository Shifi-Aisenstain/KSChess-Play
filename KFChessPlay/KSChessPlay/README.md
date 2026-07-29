# KungFu Chess

Real-time ("no turns") networked chess: both colors can move simultaneously; a move's outcome
depends on timing and per-piece cooldowns rather than alternating turns. See
[../ARCHITECTURE.md](../ARCHITECTURE.md) for the full design writeup (engine mechanics, network
protocol, package map).

## Requirements

- **JDK 17+** on `PATH` (`java -version`).
- **Maven 3.6+** on `PATH` (`mvn -version`). If you don't have Maven installed, use an IDE with
  bundled Maven (IntelliJ IDEA works out of the box) and run the `mvn` goals below from its Maven
  tool window instead of a terminal.

## Build

From this directory (`KSChessPlay/`, where `pom.xml` lives):

```powershell
mvn -q package
```

This compiles everything, runs the unit tests (`test/Test/unit`), and produces two runnable
("shaded") jars under `target/`:

- `target/kungfu-chess-client.jar` — the Swing client (main class `Main`).
- `target/kungfu-chess-server.jar` — the WebSocket server (main class `server.ServerController`).

To skip tests while iterating: `mvn -q package -DskipTests`.

## Run

**Server** (starts a WebSocket server on port `8887`, creates/opens `kungfu_chess.db` in the
current directory for accounts/Elo, and writes `server_activity.log`):

```powershell
.\run_server.ps1
```

**Client** (connects to `ws://localhost:8887` by default; prompts for a username/password on the
console — unknown usernames are auto-registered at 1200 Elo):

```powershell
.\run_client.ps1
```

To connect to a different server, pass the URI as an argument to the client jar directly:
`java -jar target\kungfu-chess-client.jar ws://<host>:8887`.

Both scripts auto-detect whether `mvn` is on `PATH`:
- If it is, they build (`mvn -q package -DskipTests`) and run the shaded jar from `target/`.
- If it isn't (e.g. Maven only exists inside an IDE), they fall back to compiling with `javac`
  (including the Lombok annotation-processor flags — see `ARCHITECTURE.md` §1) straight from the
  dependency jars already cached in your local `~/.m2/repository`, and run from `target/classes`.
  This path requires that you've opened the project in an IDE (or run `mvn` once) so those
  dependency jars actually exist locally.

Play a full local match by running the server once and the client twice (two terminals/players);
each client's *Play* button queues into Elo-range matchmaking, or use *Room* to create/join a
specific room with another player directly.

## Configuration

Nothing is externally configurable via a config file today — the relevant constants are:

| Setting | Value | Where |
|---|---|---|
| Server port | `8887` | `server.ServerController` |
| Default client server URI | `ws://localhost:8887` | `Main.DEFAULT_SERVER_URI` (override via CLI arg) |
| SQLite DB file | `kungfu_chess.db` (created in the working directory) | `server.ServerController` |
| Server/client activity logs | `server_activity.log` / `client_activity.log` | `server.ServerController` / `Main` |
| Elo matchmaking range | ±100 | `server.ServerController` |
| Tick rate | 50ms (20/sec) | `server.game.GameSession.TICK_MS` |

## Docker

Steps 1-2 of `Server_Design.md`'s build-out plan: a `Dockerfile` (server only -
the Swing client isn't a fit for a headless container) and a
`docker-compose.yml` that also brings up real Redis and Postgres containers.

```powershell
docker compose up --build
```

Starts the server on `ws://localhost:8887`. **Redis and Postgres are present
but unused so far** - `server.ServerController` still persists to SQLite
(`kungfu_chess.db`) inside its own container. Wiring the app into Redis/Postgres
is a later step in `Server_Design.md` (§3.4-5), after the real service split
(§3.3).

## Tests

```powershell
mvn -q test
```

Unit tests live in `test/Test/unit` (not Maven's default `src/test/java` — `pom.xml` points
`testSourceDirectory` at `test`). `test/Test/integration` is reserved for integration tests but is
currently empty. See `ARCHITECTURE.md` §10 for what's covered and what isn't.

To generate an HTML coverage report (JaCoCo):

```powershell
mvn -q test
```

then open `target/site/jacoco/index.html`.
