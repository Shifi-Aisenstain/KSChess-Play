package Test.unit;

import input.BoardMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BoardMapperTest {

    private final BoardMapper mapper = new BoardMapper();

    @Test
    public void testToPixelXScalesByTileSize() {
        assertEquals(0, mapper.toPixelX(0));
        assertEquals(BoardMapper.PIXELS_PER_TILE, mapper.toPixelX(1));
        assertEquals(BoardMapper.PIXELS_PER_TILE * 3, mapper.toPixelX(3));
    }

    @Test
    public void testToPixelYScalesByTileSize() {
        assertEquals(0, mapper.toPixelY(0));
        assertEquals(BoardMapper.PIXELS_PER_TILE, mapper.toPixelY(1));
        assertEquals(BoardMapper.PIXELS_PER_TILE * 7, mapper.toPixelY(7));
    }

    @Test
    public void testFractionalTilePositionTruncates() {
        // 2.9 tiles in should still fall short of pixel column 3 * PIXELS_PER_TILE.
        int x = mapper.toPixelX(2.9);
        assertTrue(x < BoardMapper.PIXELS_PER_TILE * 3);
        assertTrue(x >= BoardMapper.PIXELS_PER_TILE * 2);
    }
}
