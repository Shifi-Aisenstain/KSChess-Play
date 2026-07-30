package server.allocator;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Picks which Game Server Shard should run a newly-allocated room (PLAN_split_6_services.md §3.4):
 * plain round-robin over a configured list of shard IDs - explicitly "not load-based yet" per the
 * plan, since there's no real utilization data to base a smarter choice on until a load test (§8)
 * exists. A single-shard deployment (today's docker-compose) is just the degenerate case of a
 * one-element list, always returning the same id.
 */
public final class ShardSelector {
    private final List<String> shardIds;
    private final AtomicInteger next = new AtomicInteger(0);

    public ShardSelector(List<String> shardIds) {
        if (shardIds.isEmpty()) {
            throw new IllegalArgumentException("ShardSelector needs at least one shard id");
        }
        this.shardIds = List.copyOf(shardIds);
    }

    public String next() {
        int index = Math.floorMod(next.getAndIncrement(), shardIds.size());
        return shardIds.get(index);
    }
}
