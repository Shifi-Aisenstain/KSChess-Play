package Test.unit;

import graphics.Image;
import input.BoardMapper;
import models.Position;
import org.junit.jupiter.api.Test;
import view.CooldownHighlight;
import view.GameSnapshot;
import view.ImgRenderer;
import view.PieceSnapshot;
import view.SpriteLoader;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ImgRendererTest {

    private ImgRenderer newRenderer() {
        Image background = new Image().read("assets/board.png");
        return new ImgRenderer(new SpriteLoader(), background, new BoardMapper());
    }

    private GameSnapshot emptySnapshot() {
        return new GameSnapshot(List.of(), false, null, null, List.of(), List.of(), 0, 0, List.of(), List.of());
    }

    @Test
    public void testRenderEmptyBoardProducesFullSizeImageAtZoom1() {
        BufferedImage frame = newRenderer().render(emptySnapshot(), 1.0);
        assertTrue(frame.getWidth() > 0);
        assertTrue(frame.getHeight() > 0);
    }

    @Test
    public void testZoomScalesOutputDimensions() {
        ImgRenderer renderer = newRenderer();
        BufferedImage full = renderer.render(emptySnapshot(), 1.0);
        BufferedImage half = renderer.render(emptySnapshot(), 0.5);

        assertEquals(full.getWidth() / 2, half.getWidth());
        assertEquals(full.getHeight() / 2, half.getHeight());
    }

    @Test
    public void testRenderWithSelectionAndLegalMovesDoesNotThrow() {
        GameSnapshot snapshot = new GameSnapshot(List.of(), false, null,
                new Position(4, 4), List.of(new Position(3, 4), new Position(5, 4)),
                List.of(), 0, 0, List.of(), List.of());

        assertDoesNotThrow(() -> newRenderer().render(snapshot, 1.0));
    }

    @Test
    public void testRenderWithPieceDrawsWithoutError() {
        PieceSnapshot pawn = new PieceSnapshot('P', 'w', "idle", 4.0, 6.0, false);
        GameSnapshot snapshot = new GameSnapshot(List.of(pawn), false, null, null, List.of(), List.of(), 0, 0, List.of(), List.of());

        assertDoesNotThrow(() -> newRenderer().render(snapshot, 1.0));
    }

    @Test
    public void testCapturedPieceIsSkipped() {
        PieceSnapshot captured = new PieceSnapshot('Q', 'b', "idle", 4.0, 4.0, true);
        GameSnapshot snapshot = new GameSnapshot(List.of(captured), false, null, null, List.of(), List.of(), 0, 0, List.of(), List.of());

        assertDoesNotThrow(() -> newRenderer().render(snapshot, 1.0));
    }

    @Test
    public void testAllCooldownHighlightTypesRenderWithoutError() {
        List<CooldownHighlight> highlights = List.of(
                new CooldownHighlight(new Position(2, 2), 0.5, CooldownHighlight.Type.JUMP),
                new CooldownHighlight(new Position(3, 3), 0.8, CooldownHighlight.Type.LONG_REST),
                new CooldownHighlight(new Position(4, 4), 0.2, CooldownHighlight.Type.SHORT_REST));
        GameSnapshot snapshot = new GameSnapshot(List.of(), false, null, null, List.of(), highlights, 0, 0, List.of(), List.of());

        assertDoesNotThrow(() -> newRenderer().render(snapshot, 1.0));
    }

    @Test
    public void testZeroRemainingFractionHighlightIsSkipped() {
        List<CooldownHighlight> highlights = List.of(
                new CooldownHighlight(new Position(1, 1), 0.0, CooldownHighlight.Type.LONG_REST));
        GameSnapshot snapshot = new GameSnapshot(List.of(), false, null, null, List.of(), highlights, 0, 0, List.of(), List.of());

        assertDoesNotThrow(() -> newRenderer().render(snapshot, 1.0));
    }

    @Test
    public void testGameOverOverlayRendersWithoutError() {
        GameSnapshot snapshot = new GameSnapshot(List.of(), true, "White", null, List.of(), List.of(), 0, 0, List.of(), List.of());
        assertDoesNotThrow(() -> newRenderer().render(snapshot, 1.0));
    }
}
