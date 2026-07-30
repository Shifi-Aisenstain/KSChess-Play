package Test.unit;

import org.junit.jupiter.api.Test;
import server.allocator.ShardSelector;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ShardSelectorTest {

    @Test
    public void testSingleShardAlwaysReturnsIt() {
        ShardSelector selector = new ShardSelector(List.of("shard-1"));

        assertEquals("shard-1", selector.next());
        assertEquals("shard-1", selector.next());
        assertEquals("shard-1", selector.next());
    }

    @Test
    public void testMultipleShardsRoundRobinInOrderAndWrapAround() {
        ShardSelector selector = new ShardSelector(List.of("shard-1", "shard-2", "shard-3"));

        assertEquals("shard-1", selector.next());
        assertEquals("shard-2", selector.next());
        assertEquals("shard-3", selector.next());
        assertEquals("shard-1", selector.next(), "should wrap back around to the first shard");
    }

    @Test
    public void testEmptyListRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ShardSelector(List.of()));
    }
}
