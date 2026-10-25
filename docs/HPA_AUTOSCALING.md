# Autoscaling (Server_Design.md step 7)

Step 7 asks for two HPAs: WS Gateway scaled on connection count, and Game Server Shards scaled on a
custom "active rooms" metric via a Prometheus adapter. Both need real metrics to scale on - that's
what step 30 of this session's work (the `/metrics` endpoints, `shared.metrics.MetricsRegistry`) was
for: `gateway_active_connections` and `shard_active_games` are exactly the two gauges these HPAs read.

## The dependency chain

```
shared.metrics.MetricsRegistry (in every pod)
        │  scraped via prometheus.io/scrape annotations (20/21-*.yaml)
        ▼
k8s/40-prometheus.yaml (in-cluster Prometheus)
        │  exposes gateway_active_connections / shard_active_games as normal Prometheus series
        ▼
prometheus-adapter (Helm chart, not included here - see 41-prometheus-adapter-rules.yaml's own
        │  comment for the install command) + k8s/41-prometheus-adapter-rules.yaml's rules
        │  turns those series into the custom.metrics.k8s.io API
        ▼
k8s/42-hpa-gateway.yaml / k8s/43-hpa-shard.yaml (HorizontalPodAutoscaler objects)
        │  read from custom.metrics.k8s.io, scale the gateway/shard Deployments
        ▼
kube-controller-manager's HPA controller actually changes replica counts
```

Every link has to be in place before the HPAs do anything; with the adapter missing, `kubectl get hpa`
would just show `<unknown>` for the current metric value forever. None of this has been run against a
live cluster in this session (no K3s/Helm available in the sandbox this was written in) - review it as
correct-on-paper config needing real-cluster validation, same caveat as `k8s/README.md`.

## Why the target values are not Server_Design.md §4's production numbers

§4 assumes ~50,000 WS connections per gateway pod and ~3,000 active rooms per shard pod (both
explicitly marked there as needing load-test validation before being trusted as real budgets - see
`LOAD_TEST.md`). `k8s/42-hpa-gateway.yaml` targets an average of 500 connections/pod instead - a
demo-scale number chosen so the HPA visibly reacts to `k8s/README.md`'s single-node K3s setup without
needing tens of thousands of real WebSocket clients to prove the pipeline works. Once `LOAD_TEST.md`'s
harness (scaled up, from real load-generating machines against a real multi-node cluster) validates
what one gateway pod can actually sustain, that number - not 500 - is what production's HPA should use.

## Why the shard HPA is a documented no-op today

`k8s/43-hpa-shard.yaml` sets `maxReplicas: 1`, matching `k8s/20-shard.yaml`'s Deployment (also pinned
to 1 replica) - so applying it is safe, but it will never actually scale anything. That's intentional,
not an oversight: `GameShardRuntime` assumes exactly one process owns a given `SHARD_ID` (read from a
static ConfigMap value shared by every replica), so a second shard pod today would be a second process
fighting over the same rooms, not genuine horizontal capacity.

Making shard scaling real needs, in order:

1. Migrate `k8s/20-shard.yaml` from a `Deployment` to a `StatefulSet`, deriving each pod's `SHARD_ID`
   from its stable ordinal (`shard-$(hostname | grep -o '[0-9]*$')` or equivalent init-container
   logic) instead of the shared ConfigMap's fixed `shard-1`.
2. Replace `k8s/allocator`'s static `SHARD_IDS` ConfigMap value with something that reflects the
   StatefulSet's *current* replica count at request time - a Kubernetes API lookup, a sidecar that
   rewrites the ConfigMap on scale events, or (more in the spirit of Server_Design.md §2's own
   suggestion) moving this whole responsibility to Agones' `Fleet`/`GameServerAllocation` CRDs instead
   of hand-rolling it - see `MULTI_REGION_AND_AGONES.md`.
3. Only then raise `k8s/43-hpa-shard.yaml`'s `maxReplicas` past 1 and let it actually scale.

None of that is a YAML change - it is real Java code in `server.allocator`/`server.shard` plus the
"room_id → shard registry" gap `Server_Design.md` §6 already flags as unimplemented. This file and
`k8s/43-hpa-shard.yaml` exist to show the *shape* step 7 asks for and exactly what blocks it from being
more than that shape today, per this session's "do everything, but be honest about what's really
finished" approach.
