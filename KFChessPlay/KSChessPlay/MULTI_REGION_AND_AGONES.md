# Multi-region and Agones (Server_Design.md steps 9-10)

Server_Design.md §3 lists these as the last two build steps, explicitly after load testing (step 8):
"9. מעבר לרב-אזורי (multi-region) לפרודקשן אמיתי" and "10. שקילת Agones ברגע שה-allocator הביתי
מוכיח את הרעיון בסקאלה קטנה." Both are architecture decisions for a scale this project is nowhere
near yet (§4's ~10,000,000-concurrent-player planning target, not the single-K3s-node demo `k8s/`
actually deploys) - this document is the write-up those two steps asked for, not running code. Where
CRD YAML is included (`k8s/agones/`), it is explicitly illustrative, not deployed or deployable as-is.

## Step 9: multi-region

### What replicates per region

Every piece in `k8s/` except the public entrypoints' DNS: a full copy of `shard`, `gateway`,
`matchmaker`, `allocator`, `apigateway`, `elowriter`, `nats`, `redis` per region. The reasoning follows
directly from what's already true of a single region's design:

- **Matchmaking should stay region-local.** `MatchmakingServiceImpl`'s `EloRangeMatchCriteria` has no
  concept of network latency between players today - pairing someone in Frankfurt with someone in
  São Paulo would satisfy the Elo check while handing them a WebSocket relay latency neither signed up
  for. Each region's Matchmaker only ever sees requests from clients GeoDNS routed to that region, so
  this falls out for free rather than needing new matching logic - see "routing" below.
- **Redis (queue + active-room registry) is inherently regional.** `RedisWaitingPlayersQueue`/
  `RedisActiveRoomRegistry` back exactly the matchmaking/room state that should never cross a region
  boundary in the first place, per the point above.
- **NATS is regional** because everything subscribing to it (gateway, shard, matchmaker, allocator,
  elowriter) is regional - a single NATS cluster spanning regions would just add cross-region latency
  to every internal hop for no benefit.

### What does not cleanly replicate: Postgres

Users, Elo, and game history are the one part of the system where "which region" is genuinely
ambiguous - a player might log in from a different region than they last played in (travel, VPN, a
region failover). Two honest options, in increasing order of complexity:

1. **Single global Postgres primary**, regions' `apigateway`/`elowriter` write to it over the WAN.
   Simplest to reason about, correctness is trivial (one source of truth), but every login and every
   Elo/history write pays cross-region latency, and the primary region becomes a single point of
   failure for every other region's login/history traffic.
2. **Per-region Postgres primaries + async cross-region replication** (logical replication, or an
   outbox-pattern event stream over the same NATS-per-region already in place) reconciling into a
   global read model for cross-region leaderboards/lookups. Lower latency for the common case (a
   player usually logs in from the same region repeatedly), but now needs conflict resolution for the
   rare case for a player logging in from two regions in quick succession - last-write-wins on Elo is
   probably fine (Elo is monotonically re-derived each game anyway), but is a real design decision that
   needs writing down, not assuming.

Server_Design.md §4's Postgres row ("~20–28 shards, כל אחד primary + replicas") already assumes
sharding by write volume, not by region specifically - reconciling that sharding scheme with per-region
primaries is exactly the kind of decision this step exists to make, and is left open here rather than
guessed at.

### Routing clients to the nearest region

`k8s/30-ingress.yaml`'s single `play.kschess.local`/`ws.kschess.local` hostnames would become
per-region hostnames behind a GeoDNS provider (Route 53 latency-based routing, Cloudflare Load
Balancing with geo-steering, etc.) resolving to whichever region's Ingress is closest to the client's
resolver - not something a single Ingress resource can do by itself, since Ingress operates within one
cluster. `Server_Design.md` §5's "endpoint ציבורי קבוע אחד" (one fixed public endpoint) still holds
*per region* - a client always talks to the same regional gateway address for the duration of a
session, reconnect included, exactly like today, just with GeoDNS deciding which region "the same
address" resolves to on first connect.

## Step 10: Agones

Server_Design.md §2 already names the target: "Agones... מחליף את הלוגיקה הביתית של 'לבחור shard,
לרשום את החדר' ב-CRDs מסוג GameServer/Fleet/GameServerAllocation" - replacing `server.allocator`'s
hand-rolled `ShardSelector` (round-robin over a static `SHARD_IDS` list) and
`RedisActiveRoomRegistry` with Agones' own fleet-of-game-servers abstraction.

### Why this is worth doing at all

The static-`SHARD_IDS`-list model this project uses today (see `k8s/20-shard.yaml`'s and
`HPA_AUTOSCALING.md`'s explanation of why the shard HPA is a no-op) does not know how to add or remove
a shard pod safely at runtime - `SHARD_IDS` is a fixed ConfigMap value, and `GameShardRuntime` assumes
its own `SHARD_ID` never changes. Agones exists specifically to solve "a fleet of stateful game-server
processes that need to be allocated, drained, and scaled safely" - the exact gap `HPA_AUTOSCALING.md`
identifies as blocking real shard autoscaling.

### What would actually change

- `k8s/20-shard.yaml`'s Deployment becomes a `Fleet` (see `k8s/agones/fleet.yaml`) - Agones manages
  the pool of shard pods directly, including pre-warming idle "Ready" ones so a new room doesn't pay
  cold-start latency.
- `server.allocator.GameAllocatorRuntime.onRoomCreate` stops calling `ShardSelector`/
  `RedisActiveRoomRegistry` and instead calls Agones' allocator API (see
  `k8s/agones/gameserverallocation.yaml`) to get back an already-Ready shard pod's address.
- `server.shard.GameServerShardMain` needs the Agones Game Server SDK wired in (a local sidecar
  HTTP/gRPC API, not an external service) to call `Ready()` on startup, `Health()` on a heartbeat, and
  `Allocate()`/`Shutdown()` around a room's actual lifecycle - **this SDK integration does not exist
  anywhere in this codebase today**; it is the actual prerequisite, not a config change.
- A `FleetAutoscaler` CRD (not included here, since it depends on the SDK integration above existing
  first) would replace `k8s/43-hpa-shard.yaml` once the above lands - Agones has its own
  buffer-based/webhook-based autoscaling model that assumes SDK-integrated game servers, so it isn't a
  drop-in swap for the Prometheus-adapter-based HPA this session already built.

### Why it's step 10, not step 6

Server_Design.md §3 itself says to consider Agones "ברגע שה-allocator הביתי מוכיח את הרעיון" (once
the home-grown allocator proves the concept) - i.e., only after steps 1-8 (containerize, split
services, Redis, Postgres, K3s, HPA, load-test) are done and working, which matches exactly where this
project is now. Adopting Agones earlier would mean debugging a new SDK integration and a new CRD
model at the same time as the basic 6-service split - strictly more moving parts for no benefit until
the simpler model's actual limits (the shard-scaling gap `HPA_AUTOSCALING.md` documents) are the thing
actually blocking progress.
