package Test.unit;

import graphics.Image;
import input.BoardMapper;
import org.junit.jupiter.api.Test;
import view.SpriteLoader;

import static org.junit.jupiter.api.Assertions.*;

public class SpriteLoaderTest {

    @Test
    public void testGetSpriteReturnsTilePixelDimensions() {
        Image sprite = new SpriteLoader().getSprite('K', 'w', "idle", 0);
        assertEquals(BoardMapper.PIXELS_PER_TILE, sprite.getWidth());
        assertEquals(BoardMapper.PIXELS_PER_TILE, sprite.getHeight());
    }

    @Test
    public void testSameFrameIsCachedAndReturnsSameInstance() {
        SpriteLoader loader = new SpriteLoader();
        Image first = loader.getSprite('Q', 'b', "idle", 2);
        Image second = loader.getSprite('Q', 'b', "idle", 2);
        assertSame(first, second);
    }

    @Test
    public void testFrameWrapsAroundFrameCount() {
        SpriteLoader loader = new SpriteLoader();

        Image frame0 = loader.getSprite('N', 'w', "idle", 0);
        Image frameWrapped = loader.getSprite('N', 'w', "idle", SpriteLoader.FRAME_COUNT);
        assertSame(frame0, frameWrapped);
    }

    @Test
    public void testNegativeFrameWrapsToAValidSprite() {
        assertDoesNotThrow(() -> new SpriteLoader().getSprite('R', 'b', "idle", -1));
    }

    @Test
    public void testDifferentStatesAreCachedSeparately() {
        SpriteLoader loader = new SpriteLoader();
        Image idle = loader.getSprite('B', 'w', "idle", 0);
        Image moving = loader.getSprite('B', 'w', "move", 0);
        assertNotSame(idle, moving);
    }

    @Test
    public void testDifferentColorsAreCachedSeparately() {
        SpriteLoader loader = new SpriteLoader();
        Image white = loader.getSprite('P', 'w', "idle", 0);
        Image black = loader.getSprite('P', 'b', "idle", 0);
        assertNotSame(white, black);
    }

    @Test
    public void testAllSixPieceTypesLoadForBothColors() {
        SpriteLoader loader = new SpriteLoader();
        for (char type : new char[]{'K', 'Q', 'R', 'B', 'N', 'P'}) {
            for (char color : new char[]{'w', 'b'}) {
                assertNotNull(loader.getSprite(type, color, "idle", 0));
            }
        }
    }
}
