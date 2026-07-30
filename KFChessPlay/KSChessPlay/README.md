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

This compiles everything, runs the unit tests (`test/Test/unit`), and produces seven runnable
("shaded") jars under `target/`, one per process (see `PLAN_split_6_services.md` for the full
service-split story):

- `target/kungfu-chess-client.jar` — the Swing client (main class `Main`).
- `target/kungfu-chess-shard.jar` — Game Server Shard, runs the actual game engine (`server.shard.GameServerShardMain`).
- `target/kungfu-chess-gateway.jar` — WS Gateway, holds every client WebSocket (`server.gateway.WsGatewayMain`).
- `target/kungfu-chess-matchmaker.jar` — Elo-range matchmaking queue (`server.matchmaking.MatchmakerMain`).
- `target/kungfu-chess-allocator.jar` — room creation/joining + Shard selection (`server.allocator.GameAllocatorMain`).
- `target/kungfu-chess-apigateway.jar` — `POST /login` over REST (`server.apigateway.ApiGatewayMain`).
- `target/kungfu-chess-elowriter.jar` — batches Elo updates into storage (`server.persistence.EloWriterMain`).

There is no single "the server" jar/process anymore - the backend is these 6 independent processes
(plus NATS/Redis/Postgres to talk to each other and hold shared state) rather than one monolith.

To skip tests while iterating: `mvn -q package -DskipTests`.

## Run

**Backend** (all 6 processes + NATS/Redis/Postgres) - see the [Docker](#docker) section below;
`docker compose up --build` is the supported way to run the full backend, since running 6 JVMs plus
NATS/Redis/Postgres by hand isn't practical.

**Client** (connects to the WS Gateway at `ws://localhost:8887` and the API Gateway at
`http://localhost:8080` by default; prompts for a username/password on the console — unknown
usernames are auto-registered at 1200 Elo):

```powershell
.\run_client.ps1
```

To connect to a different backend: `.\run_client.ps1 ws://<host>:8887 http://<host>:8080`, or
directly: `java -jar target\kungfu-chess-client.jar ws://<host>:8887 http://<host>:8080`.

`run_client.ps1`/`build-and-run.ps1` auto-detect whether `mvn` is on `PATH`:
- If it is, they build (`mvn -q package -DskipTests`) and run the shaded jar from `target/`.
- If it isn't (e.g. Maven only exists inside an IDE), they fall back to compiling with `javac`
  (including the Lombok annotation-processor flags — see `ARCHITECTURE.md` §1) straight from the
  dependency jars already cached in your local `~/.m2/repository`, and run from `target/classes`.
  This path requires that you've opened the project in an IDE (or run `mvn` once) so those
  dependency jars actually exist locally.

Play a full local match: start the backend (`docker compose up --build`), then run the client twice
(two terminals/players); each client's *Play* button queues into Elo-range matchmaking, or use
*Room* to create/join a specific room with another player directly.

## Configuration

Nothing is externally configurable via a config file today — every process reads its settings from
environment variables (see `docker-compose.yml` for the values each one is actually started with):

| Setting | Env var | Default | Process |
|---|---|---|---|
| WS Gateway port | `PORT` | `8887` | `server.gateway.WsGatewayMain` |
| API Gateway port | `PORT` | `8080` | `server.apigateway.ApiGatewayMain` |
| Health-check port | `HEALTH_PORT` | `8081` | every backend process except API Gateway, which folds `/health` onto its own `PORT` instead (see `shared.health.HealthServer`) |
| NATS URL | `NATS_URL` | `nats://nats:4222` | Shard/Gateway/Matchmaker/Allocator/Elo writer (not API Gateway - it never touches NATS) |
| Redis URL | `REDIS_URL` | `redis://redis:6379` | Matchmaker (waiting-players queue), Allocator (active-room registry) - see `shared.redis` |
| Postgres URL (JDBC, credentials as query params) | `POSTGRES_URL` | *(unset - falls back to SQLite)* | API Gateway, Elo writer - the only two processes that touch a database at all; unset means `server.config.PersistenceConfig` uses SQLite instead (`DB_FILE`, default `kungfu_chess.db`) - the local dev/demo default per `Server_Design.md` |
| This Shard's id | `SHARD_ID` | `shard-1` | `server.shard.GameServerShardMain` (must appear in Allocator's `SHARD_IDS`) |
| Allocator's known shard ids | `SHARD_IDS` (comma-separated) | `shard-1` | `server.allocator.GameAllocatorMain` |
| Default client WS/API addresses | CLI args | `ws://localhost:8887` / `http://localhost:8080` | `Main` |
| Elo matchmaking range | (constant, not env) | ±100 | `server.matchmaking.MatchmakerMain` |
| Tick rate | (constant, not env) | 50ms (20/sec) | `server.game.GameSession.TICK_MS` |

## Docker

`docker-compose.yml` runs the full backend: all 6 processes above, each built from the single
build-arg-driven root `Dockerfile` (`JAR_CLASSIFIER` picks which shaded jar becomes that container's
`app.jar`), plus NATS/Redis/Postgres - all three are actually wired into the app now (NATS for every
inter-process call, Redis for the Matchmaker/Allocator per `Server_Design.md` step 4, Postgres for
the API Gateway/Elo writer per step 5).

```powershell
docker compose up --build
```

Exposes the WS Gateway on `ws://localhost:8887` and the API Gateway (login) on
`http://localhost:8080` - point the Swing client at both (see [Run](#run) above). The Swing client
itself is not part of this compose file - it isn't a fit for a headless container - run it locally
with `run_client.ps1` against the Dockerized backend.

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
