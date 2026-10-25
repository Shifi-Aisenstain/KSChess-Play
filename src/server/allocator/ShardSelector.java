package server.allocator;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
