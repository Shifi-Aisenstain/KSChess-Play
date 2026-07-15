package view;

import graphics.Image;
import input.BoardMapper;
import models.Position;

import java.awt.Color;

public class ImgRenderer {
    // How fast the idle "breathing" cycle advances through its 5 frames.
    private static final long FRAME_DURATION_MS = 150;

    private static final Color SELECTED_HIGHLIGHT = new Color(255, 235, 59, 130);
    private static final Color LEGAL_MOVE_HIGHLIGHT = new Color(76, 175, 80, 110);

    private SpriteLoader spriteLoader;
    private Image backgroundImg;
    private BoardMapper mapper;

    public ImgRenderer(SpriteLoader spriteLoader, Image backgroundImg, BoardMapper mapper) {
        this.spriteLoader = spriteLoader;
        this.backgroundImg = backgroundImg;
        this.mapper = mapper;
    }

    public void render(GameSnapshot snapshot, Image canvas) {
        backgroundImg.drawOn(canvas, 0, 0);

        if (snapshot.getSelectedPosition() != null) {
            highlightSquare(canvas, snapshot.getSelectedPosition(), SELECTED_HIGHLIGHT);
        }
        for (Position dest : snapshot.getLegalMoves()) {
            highlightSquare(canvas, dest, LEGAL_MOVE_HIGHLIGHT);
        }

        // One shared frame index per render pass, so every idle piece
        // breathes in sync rather than each picking its own random frame.
        int frame = (int) ((System.currentTimeMillis() / FRAME_DURATION_MS) % SpriteLoader.FRAME_COUNT);

        for (PieceSnapshot piece : snapshot.getPieces()) {
            Image sprite = spriteLoader.getSprite(piece.type, piece.color, piece.state, frame);
            int x = mapper.toPixelX(piece.x);
            int y = mapper.toPixelY(piece.y);
            sprite.drawOn(canvas, x, y);
        }
    }

    private void highlightSquare(Image canvas, Position pos, Color color) {
        int x = mapper.toPixelX(pos.getCol());
        int y = mapper.toPixelY(pos.getRow());
        canvas.fillRectAlpha(x, y, BoardMapper.PIXELS_PER_TILE, BoardMapper.PIXELS_PER_TILE, color);
    }
}