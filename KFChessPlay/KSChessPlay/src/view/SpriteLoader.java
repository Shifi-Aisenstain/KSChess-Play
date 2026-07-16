package view;

import graphics.Image;
import input.BoardMapper;

import java.awt.Color;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;

public class SpriteLoader {

    public static final int FRAME_COUNT = 5;

    private static final String ASSETS_ROOT = "assets";
    private static final int BACKGROUND_TOLERANCE = 20;

    private final Map<String, Image> cache = new HashMap<>();

    public Image getSprite(char type, char color, String state, int frame) {
        String folderName = "" + type + Character.toUpperCase(color);
        int frameNumber = Math.floorMod(frame, FRAME_COUNT) + 1;
        String cacheKey = folderName + "/" + state + "/" + frameNumber;

        Image cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String path = ASSETS_ROOT + "/" + folderName + "/states/" + state + "/sprites/" + frameNumber + ".png";
        Image sprite = new Image();

        sprite.read(path,
                new Dimension(BoardMapper.PIXELS_PER_TILE, BoardMapper.PIXELS_PER_TILE),
                true,
                null);

        cache.put(cacheKey, sprite);
        return sprite;
    }
}
