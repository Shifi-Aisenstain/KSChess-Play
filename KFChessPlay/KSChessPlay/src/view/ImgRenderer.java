package view;
import graphics.Image;
import input.BoardMapper;

public class ImgRenderer {
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
        for (PieceSnapshot piece : snapshot.getPieces()) {
            Image sprite = spriteLoader.getSprite(piece.type, piece.color, piece.state);
            int x = mapper.toPixelX(piece.x);
            int y = mapper.toPixelY(piece.y);
            sprite.drawOn(canvas, x, y);
        }
    }
}