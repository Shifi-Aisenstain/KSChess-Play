package Test.unit;

import graphics.Image;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** All operations here are plain in-memory raster manipulation (ImageIO/Graphics2D/BufferedImage) -
  * headless-safe, no real display needed. Only Image.show() (opens a real JFrame) is out of scope,
  * consistent with excluding Swing UI elsewhere. */
public class ImageTest {

    private String writePng(Path dir, String name, int width, int height, boolean withAlpha, Color fill) throws IOException {
        BufferedImage img = new BufferedImage(width, height,
                withAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(fill);
        g.fillRect(0, 0, width, height);
        g.dispose();
        Path file = dir.resolve(name);
        ImageIO.write(img, "png", file.toFile());
        return file.toString();
    }

    @Test
    public void testReadWithoutResizeKeepsOriginalDimensions(@TempDir Path dir) throws IOException {
        String path = writePng(dir, "plain.png", 40, 20, true, Color.RED);
        Image image = new Image().read(path);
        assertEquals(40, image.getWidth());
        assertEquals(20, image.getHeight());
    }

    @Test
    public void testReadWithResizeIgnoringAspect(@TempDir Path dir) throws IOException {
        String path = writePng(dir, "square.png", 10, 10, true, Color.BLUE);
        Image image = new Image().read(path, new Dimension(50, 30), false, null);
        assertEquals(50, image.getWidth());
        assertEquals(30, image.getHeight());
    }

    @Test
    public void testReadWithResizeKeepingAspect(@TempDir Path dir) throws IOException {
        // 100x50 (2:1) fit into a 40x40 box, keeping aspect -> scale limited by width: 40x20.
        String path = writePng(dir, "wide.png", 100, 50, true, Color.GREEN);
        Image image = new Image().read(path, new Dimension(40, 40), true, null);
        assertEquals(40, image.getWidth());
        assertEquals(20, image.getHeight());
    }

    @Test
    public void testReadMissingFileThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Image().read("no/such/file.png"));
    }

    @Test
    public void testReadNonImageFileThrows(@TempDir Path dir) throws IOException {
        Path notAnImage = dir.resolve("not-an-image.png");
        java.nio.file.Files.writeString(notAnImage, "this is definitely not a PNG");
        assertThrows(IllegalArgumentException.class, () -> new Image().read(notAnImage.toString()));
    }

    @Test
    public void testMakeColorTransparentClearsMatchingPixelsOnly(@TempDir Path dir) throws IOException {
        String path = writePng(dir, "bg.png", 4, 4, false, Color.WHITE);
        Image image = new Image().read(path);
        image.makeColorTransparent(Color.WHITE, 10);

        int rgb = image.get().getRGB(0, 0);
        int alpha = (rgb >>> 24) & 0xFF;
        assertEquals(0, alpha, "white background pixel should be made fully transparent");
    }

    @Test
    public void testMakeColorTransparentBeforeLoadThrows() {
        assertThrows(IllegalStateException.class, () -> new Image().makeColorTransparent(Color.WHITE, 10));
    }

    @Test
    public void testFillRectAlphaPaintsRequestedRegion(@TempDir Path dir) throws IOException {
        String path = writePng(dir, "canvas.png", 10, 10, true, Color.BLACK);
        Image image = new Image().read(path);

        image.fillRectAlpha(0, 0, 10, 10, new Color(255, 0, 0, 128));

        int rgb = image.get().getRGB(5, 5);
        int red = (rgb >> 16) & 0xFF;
        assertTrue(red > 0, "expected some red to have been blended in");
    }

    @Test
    public void testDrawOnComposesOntoDestination(@TempDir Path dir) throws IOException {
        String bgPath = writePng(dir, "dest.png", 20, 20, true, Color.BLACK);
        String patchPath = writePng(dir, "patch.png", 5, 5, true, Color.RED);

        Image background = new Image().read(bgPath);
        Image patch = new Image().read(patchPath);
        patch.drawOn(background, 2, 2);

        int rgb = background.get().getRGB(3, 3);
        assertEquals(0xFF0000, rgb & 0xFFFFFF);
    }

    @Test
    public void testDrawOnBeyondBoundsThrows(@TempDir Path dir) throws IOException {
        String bgPath = writePng(dir, "small-dest.png", 4, 4, true, Color.BLACK);
        String patchPath = writePng(dir, "big-patch.png", 10, 10, true, Color.RED);

        Image background = new Image().read(bgPath);
        Image patch = new Image().read(patchPath);

        assertThrows(IllegalArgumentException.class, () -> patch.drawOn(background, 0, 0));
    }

    @Test
    public void testDrawOnUnloadedImageThrows() {
        assertThrows(IllegalStateException.class, () -> new Image().drawOn(new Image(), 0, 0));
    }

    @Test
    public void testPutTextDoesNotThrow(@TempDir Path dir) throws IOException {
        String path = writePng(dir, "textcanvas.png", 100, 40, true, Color.WHITE);
        Image image = new Image().read(path);
        assertDoesNotThrow(() -> image.putText("Hi", 5, 20, 1.0f, Color.BLACK, 1));
    }

    @Test
    public void testPutTextBeforeLoadThrows() {
        assertThrows(IllegalStateException.class, () -> new Image().putText("x", 0, 0, 1.0f, Color.BLACK, 1));
    }

    @Test
    public void testRealBoardPngLoadsSuccessfully() {
        Image image = new Image().read("assets/board.png");
        assertTrue(image.getWidth() > 0);
        assertTrue(image.getHeight() > 0);
    }
}
