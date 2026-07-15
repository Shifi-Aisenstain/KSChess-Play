package view;

import graphics.Image;
import input.BoardMapper;

import java.awt.Color;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads (and caches) the sprite image for a given piece type/color/state/
 * animation frame. Single responsibility: resolve that quadruple to an
 * Image. Each state folder in assets/ has 5 numbered frames (1.png..5.png) -
 * that's what drives the "breathing" idle animation and the walking motion
 * during a move; ImgRenderer decides which frame to ask for.
 */
public class SpriteLoader {

    // Every state folder in assets/ ships exactly 5 frames.
    public static final int FRAME_COUNT = 5;

    private static final String ASSETS_ROOT = "assets";
    // The sprite PNGs are drawn on a flat white rectangle; this is how close
    // a pixel has to be to pure white to be treated as "background" and
    // erased. Piece fill colors in these assets sit at least 25 levels away
    // from white per channel, so this is a safe margin.
    private static final int BACKGROUND_TOLERANCE = 20;

    private final Map<String, Image> cache = new HashMap<>();

    public Image getSprite(char type, char color, String state, int frame) {
        // Asset folders are named "<Type><COLOR>" with an UPPERCASE color letter
        // (e.g. assets/KW, assets/RB) - but Piece stores color as lowercase
        // ('w'/'b'). Without normalizing the case here, the path never matches
        // the real folder name, which is why sprites failed to load.
        String folderName = "" + type + Character.toUpperCase(color);
        int frameNumber = Math.floorMod(frame, FRAME_COUNT) + 1; // files are 1-indexed
        String cacheKey = folderName + "/" + state + "/" + frameNumber;

        Image cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String path = ASSETS_ROOT + "/" + folderName + "/states/" + state + "/sprites/" + frameNumber + ".png";
        Image sprite = new Image();

        // Raw sprite files (e.g. 298x330) are much larger than a board tile
        // (BoardMapper.PIXELS_PER_TILE). Drawing them at native size makes
        // Image.drawOn() throw once a piece's bounding box runs past the
        // canvas edge, which silently aborts rendering for the rest of that
        // frame - that's why pieces were missing from the board. Scaling to
        // the tile size here (reusing Image's existing resize support, and
        // sharing the same constant BoardMapper uses to place pieces) keeps
        // sprite size and tile size from ever drifting apart again.
        sprite.read(path,
                new Dimension(BoardMapper.PIXELS_PER_TILE, BoardMapper.PIXELS_PER_TILE),
                true,
                null);

        // Strip the flat white background so the checkerboard square color
        // underneath (and which player it belongs to) stays visible.
        sprite.makeColorTransparent(Color.WHITE, BACKGROUND_TOLERANCE);

        cache.put(cacheKey, sprite);
        return sprite;
    }
}