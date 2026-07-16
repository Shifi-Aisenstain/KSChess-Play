package view;

import graphics.Image;
import input.BoardMapper;
import models.Position;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImgRenderer {
    private static final long FRAME_DURATION_MS = 150;
    private static final Color SELECTED_HIGHLIGHT   = new Color(255, 235, 59, 130);
    private static final Color LEGAL_MOVE_HIGHLIGHT = new Color(76, 175, 80, 110);

    private static final Color LONG_REST_COLOR  = new Color(33,  150, 243, 160);
    private static final Color JUMP_COLOR       = LONG_REST_COLOR;
    private static final Color SHORT_REST_COLOR = LONG_REST_COLOR;

    private final SpriteLoader spriteLoader;
    private final Image backgroundImg;
    private final BoardMapper mapper;

    public ImgRenderer(SpriteLoader spriteLoader, Image backgroundImg, BoardMapper mapper) {
        this.spriteLoader = spriteLoader;
        this.backgroundImg = backgroundImg;
        this.mapper = mapper;
    }

    public BufferedImage render(GameSnapshot snapshot, double zoom) {
        int tileSize = (int) (BoardMapper.PIXELS_PER_TILE * zoom);
        int boardW   = (int) (backgroundImg.getWidth()  * zoom);
        int boardH   = (int) (backgroundImg.getHeight() * zoom);

        BufferedImage frame = new BufferedImage(boardW, boardH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        g.drawImage(backgroundImg.get(), 0, 0, boardW, boardH, null);

        if (snapshot.getSelectedPosition() != null) {
            drawHighlight(g, snapshot.getSelectedPosition(), SELECTED_HIGHLIGHT, tileSize);
        }
        for (Position dest : snapshot.getLegalMoves()) {
            drawHighlight(g, dest, LEGAL_MOVE_HIGHLIGHT, tileSize);
        }

        for (CooldownHighlight ch : snapshot.getCooldownHighlights()) {
            drawCooldownBar(g, ch, tileSize);
        }

        int frameIdx = (int) ((System.currentTimeMillis() / FRAME_DURATION_MS) % SpriteLoader.FRAME_COUNT);
        for (PieceSnapshot piece : snapshot.getPieces()) {
            if (piece.isCaptured) continue;
            Image sprite = spriteLoader.getSprite(piece.type, piece.color, piece.state, frameIdx);
            int x = (int) (piece.x * tileSize);
            int y = (int) (piece.y * tileSize);
            g.drawImage(sprite.get(), x, y, tileSize, tileSize, null);
        }

        if (snapshot.isGameOver()) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, boardW, boardH);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, (int) (60 * zoom)));
            g.drawString(snapshot.getGameOverMessage(), (int) (80 * zoom), boardH / 2);
        }

        g.dispose();
        return frame;
    }

    private void drawHighlight(Graphics2D g, Position pos, Color color, int tileSize) {
        int x = pos.getCol() * tileSize;
        int y = pos.getRow() * tileSize;
        g.setColor(color);
        g.fillRect(x, y, tileSize, tileSize);
    }

    private void drawCooldownBar(Graphics2D g, CooldownHighlight ch, int tileSize) {
        double fraction = ch.getRemainingFraction();
        if (fraction <= 0) return;

        int x = ch.getPosition().getCol() * tileSize;
        int squareY = ch.getPosition().getRow() * tileSize;
        int fillH = (int) (tileSize * fraction);
        int fillY = squareY + tileSize - fillH;

        g.setColor(barColor(ch.getType()));
        g.fillRect(x, fillY, tileSize, fillH);
    }

    private Color barColor(CooldownHighlight.Type type) {
        switch (type) {
            case JUMP:       return JUMP_COLOR;
            case LONG_REST:  return LONG_REST_COLOR;
            case SHORT_REST: return SHORT_REST_COLOR;
            default:         return JUMP_COLOR;
        }
    }
}
