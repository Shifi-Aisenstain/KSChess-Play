# K3s manifests (Server_Design.md step 6)

These manifests translate `docker-compose.yml`'s 9 services (6 application processes + nats/redis/
postgres) into the K3s deployment Server_Design.md §3 step 6 calls for: "Deployment+Service לכל אחד
מ-6 הרכיבים, Redis, Postgres... הנקודה הראשונה שבה 'הרבה Pods, registry אחד' באמת רץ." They have not
been applied against a live cluster in this session (no K3s/kubectl available in the sandbox this
was written in) - review them as correct-on-paper config, then validate on a real cluster before
trusting them the way the docker-compose stack has already been hands-on verified.

## Building images

Unlike docker-compose (which builds each service inline via `build: args: JAR_CLASSIFIER`), K3s pulls
pre-built images by name. Build and tag one image per classifier from the repo root, then either push
them to a registry K3s can reach or `k3s ctr images import` them directly on a single-node cluster:

```
for classifier in shard gateway matchmaker allocator apigateway elowriter; do
  docker build --build-arg JAR_CLASSIFIER=$classifier -t kungfu-chess:$classifier .
done
```

The manifests below reference `kungfu-chess:<classifier>` exactly as built above - update the image
field in each `*.yaml` (or re-tag/push to your registry of choice, e.g. `ghcr.io/you/kungfu-chess:shard`)
before applying if you're not using `k3s ctr images import` on a single node.

## Applying

Numeric prefixes are the intended apply order (namespace and config first, infra before the app
services that depend on it):

```
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-secrets.yaml -f k8s/02-configmap.yaml
kubectl apply -f k8s/10-nats.yaml -f k8s/11-redis.yaml -f k8s/12-postgres.yaml -f k8s/13-jaeger.yaml
kubectl -n kungfu-chess rollout status statefulset/postgres
kubectl apply -f k8s/20-shard.yaml -f k8s/21-gateway.yaml -f k8s/22-matchmaker.yaml \
               -f k8s/23-allocator.yaml -f k8s/24-apigateway.yaml -f k8s/25-elowriter.yaml
kubectl apply -f k8s/30-ingress.yaml
kubectl apply -f k8s/40-prometheus.yaml -f k8s/41-prometheus-adapter-rules.yaml \
               -f k8s/42-hpa-gateway.yaml -f k8s/43-hpa-shard.yaml \
               -f k8s/44-alert-rules.yaml -f k8s/45-alertmanager.yaml
```

(Or just `kubectl apply -f k8s/` - Kubernetes handles most of the create-order dependencies itself via
retries; the explicit order above just avoids the initial burst of `CrashLoopBackOff`s while Postgres
is still starting.)

## Replica counts, and why they differ per service

| Service | replicas | Why |
|---|---|---|
| gateway, matchmaker, allocator, apigateway | 2 | Stateless / Redis-backed idempotent state - safe to run more than one today, see each file's own header comment. |
| shard | 1 | `GameShardRuntime` assumes exactly one process owns a given `SHARD_ID`; see `20-shard.yaml`'s comment and `HPA_AUTOSCALING.md` for what real horizontal scaling needs. |
| elowriter | 1 | NATS fan-out (no queue-group support in `shared.bus.NatsMessageBus` yet) + plain `INSERT`s in `GameHistoryWriterRuntime` would double-insert every game/move row with >1 replica. See `25-elowriter.yaml`'s comment. |
| nats, redis, postgres | 1 | Single-node demo scope, matching docker-compose.yml exactly - a real production deployment would run NATS/Redis clusters and Postgres with replicas (Server_Design.md §4's Redis/Postgres shard-count rows), which is out of scope for this K3s-demo step. |

## Observability: metrics, alerts, traces

Server_Design.md §1's Observability box (logs, metrics, alerts, traces, load-tests) is fully wired
into this directory now:

| Concern | Files | Notes |
|---|---|---|
| Metrics | `40-prometheus.yaml` | Scrapes every pod's `/metrics` (shared.metrics.MetricsRegistry) via pod annotations. |
| Autoscaling | `41-prometheus-adapter-rules.yaml`, `42-hpa-gateway.yaml`, `43-hpa-shard.yaml` | See `HPA_AUTOSCALING.md` - the shard HPA is a deliberate no-op until a real prerequisite lands. |
| Alerts | `44-alert-rules.yaml`, `45-alertmanager.yaml` | Rules over the same metric names; Alertmanager's receiver is intentionally a no-op (no real Slack/email/webhook credentials exist) - alerts are still visible/queryable at its own UI. |
| Traces | `13-jaeger.yaml` | `gateway`/`apigateway` report Zipkin v2 spans (shared.tracing.JaegerReporter) tied together by a traceId that now rides along on `shared.protocol.Message` and every NATS bus hop; browse them at Jaeger's UI (`:16686`). |
| Logs | (no separate manifest - `./logs/<service>` volumes in docker-compose, stdout in K3s) | Every line is stamped with the active traceId (`server.logging.ServerActivityLogger`). |
| Load tests | not a k8s manifest - see `loadtest.LoadTestMain` at the repo root | Drives the real client-side protocol against a running stack, K3s or compose. |

## What this does *not* cover yet

- No NetworkPolicy - every pod in the namespace can currently reach every other pod. Server_Design.md
  §5's "shards are never on the public network" is satisfied (no Ingress/NodePort routes to `shard`),
  but nothing stops `apigateway`'s pod from opening a socket straight to `shard`'s ClusterIP either.
  Worth adding once this earns real internet-facing traffic.
- No resource-based cluster autoscaling (adding K3s *nodes*, not pods) - pod-level autoscaling (step
  7) is `40-prometheus.yaml` / `41-prometheus-adapter-rules.yaml` / `42-hpa-gateway.yaml` /
  `43-hpa-shard.yaml` in this same directory - see `HPA_AUTOSCALING.md` for how they fit together and
  why the shard one is a deliberate no-op until a real prerequisite lands.
- Alertmanager has no real notification receiver, and Jaeger's all-in-one image keeps everything in
  memory (traces/state vanish on pod restart) - both fine for this demo, not production-grade.
