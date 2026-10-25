# Load testing the docker-compose stack (Server_Design.md step 8)

`loadtest.LoadTestMain` (`src/loadtest/`) is a small harness that drives the real protocol end to end
against a running docker-compose stack: `POST /login` on the API Gateway, then a WebSocket connection to
the WS Gateway, `PLAY_REQUEST` matchmaking, one scripted `MOVE`, and a `RESIGN` to close the game out
cleanly. It reuses the exact same client-side classes the Swing client uses (`client.net.ApiGatewayClient`,
`client.net.ServerConnection`, `shared.protocol.MessageCodec`) so it is exercising the identical wire
protocol, not a reimplementation of it.

## Running it

Build the shaded jar (`mvn package` produces `target/kungfu-chess-2.0.0-loadtest.jar`), start the stack
(`docker compose up -d`), then:

```
java -jar target/kungfu-chess-2.0.0-loadtest.jar --players=50 --api-url=http://localhost:8080 --ws-url=ws://localhost:8887 --csv=loadtest-results.csv
```

`--players` must be even (each pair gets matched together by Elo-range matchmaking; all simulated accounts
start at the default 1200 Elo, so they always pair up). `--csv` is optional; when given, every simulated
player's individual timings and pass/fail flags are written there for offline analysis. Every run
auto-registers fresh throwaway accounts (`loadtest-<runId>-<playerIndex>`) via the same login-or-register
path real players use (`AuthServiceImpl.login`), so nothing needs seeding first.

## What it measures

Three latencies, each reported as min/avg/p95/max plus a pass count out of the player total:

- **Login** — `POST /login` round trip on the API Gateway.
- **Matchmaking** — time from sending `PLAY_REQUEST` to receiving `MATCH_FOUND`, i.e. WS Gateway → NATS →
  Matchmaker → Game Allocator → Game Server Shard → back through NATS → WS Gateway → client.
- **Move round trip** — time from sending one `MOVE` to receiving the next message back from the shard
  (either `STATE_UPDATE` if legal or `ERROR` if not). The harness always sends the same scripted opening
  move (e2-e4 for White, e7-e5 for Black); whichever side is Black will usually get it rejected as
  out-of-turn, which is fine — a rejection still round-trips through the entire Gateway/NATS/Shard path,
  so it's exercising the same infrastructure hop the latency number is meant to measure. Move *correctness*
  is not what this harness checks (`test/Test/unit/GameSessionConcurrencyTest.java` and friends already
  cover that against the real `GameEngine`).

It also reports how many games reached a clean `GAME_OVER` after the harness sends `RESIGN`, as a basic
end-to-end sanity check that the whole path stays healthy under concurrent load rather than just checking
individual requests in isolation.

## Extrapolating toward Server_Design.md §4

This harness runs everything — 50+ simulated players, the whole docker-compose stack — on one machine, so
its absolute throughput numbers do not translate directly into "pods needed in production." What it *is*
good for:

1. Confirming the per-pod capacity assumptions in §4's table are the right *shape* (e.g. does move latency
   stay flat as concurrent games increase, or does it degrade non-linearly — the latter would mean the
   ~3,000-rooms-per-shard-pod assumption needs to come down, not just be re-measured at a different number).
2. Catching protocol-level regressions (a bad matchmaking scan interval, a lock contention bug in
   `RoomManager`, an N+1 query in a repository) long before they'd show up in a real production load test.
3. Giving a repeatable, scriptable regression check that can run in CI against a docker-compose stack on
   every change to the shard/gateway/matchmaker/allocator code, instead of the only load signal being a
   human clicking through the Swing client.

Turning this into an actual production capacity *proof* still requires steps 6-7 (K3s + HPA, see
`k8s/README.md`) and running the equivalent harness — scaled up, from multiple load-generating machines,
against a real multi-node cluster — which is real future work, not something a single dev-machine
docker-compose run can stand in for. §4's ~1,700-2,100 shard pods / ~200-250 gateway pods / etc. remain
estimates until that happens.
