package graphics;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.io.File;
import java.io.IOException;

/**
 * Lightweight image‑utility class using only standard JDK APIs.
 */
public class Image {

    private BufferedImage img;

    /* ----------- load & optional resize ----------- */
    public Image read(String path,
                      Dimension targetSize,
                      boolean keepAspect,
                      Object interpolation /*ignored*/) {

        try {
            img = ImageIO.read(new File(path));                              // :contentReference[oaicite:0]{index=0}
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load image: " + path);
        }
        if (img == null) throw new IllegalArgumentException("Unsupported image: " + path);

        if (targetSize != null) {
            int tw = targetSize.width, th = targetSize.height;
            int w = img.getWidth(), h = img.getHeight();

            int nw, nh;
            if (keepAspect) {                                                // :contentReference[oaicite:1]{index=1}
                double s = Math.min(tw / (double) w, th / (double) h);
                nw = (int) Math.round(w * s);
                nh = (int) Math.round(h * s);
            } else { nw = tw; nh = th; }

            BufferedImage dst = new BufferedImage(
                    nw, nh,
                    img.getColorModel().hasAlpha()
                            ? BufferedImage.TYPE_INT_ARGB
                            : BufferedImage.TYPE_INT_RGB);

            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);   // :contentReference[oaicite:2]{index=2}
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();
            img = dst;
        }
        return this;
    }

    public Image read(String path) { return read(path, null, false, null); }

    /* ----------- strip a flat background color to transparency ----------- */
    /**
     * Many of the sprite PNGs in assets/ have no alpha channel - they're a
     * piece drawn on a flat white rectangle. Drawn as-is, that white
     * rectangle covers the checkerboard square underneath it, which is why
     * pieces looked like they were sitting on opaque white/gray tiles.
     * This converts the image to ARGB and makes every pixel within
     * `tolerance` of `keyColor` fully transparent, leaving the piece itself
     * untouched (piece fill colors in these assets differ from the
     * background by more than the tolerance used here).
     */
    public Image makeColorTransparent(Color keyColor, int tolerance) {
        if (img == null) throw new IllegalStateException("Image not loaded.");

        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int kr = keyColor.getRed(), kg = keyColor.getGreen(), kb = keyColor.getBlue();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                boolean isBackground = Math.abs(r - kr) <= tolerance
                        && Math.abs(g - kg) <= tolerance
                        && Math.abs(b - kb) <= tolerance;

                out.setRGB(x, y, isBackground ? (rgb & 0x00FFFFFF) : (0xFF000000 | rgb));
            }
        }

        img = out;
        return this;
    }

    /* ----------- flat translucent rectangle (selection / move highlights) ----------- */
    public void fillRectAlpha(int x, int y, int w, int h, Color color) {
        if (img == null) throw new IllegalStateException("Image not loaded.");

        Graphics2D g = img.createGraphics();
        g.setColor(color); // an ARGB Color's alpha is honored automatically
        g.fillRect(x, y, w, h);
        g.dispose();
    }

    /* ----------- draw this image onto another ----------- */
    public void drawOn(Image other, int x, int y) {
        if (img == null || other.img == null)
            throw new IllegalStateException("Both images must be loaded.");

        if (x + img.getWidth()  > other.img.getWidth()
                || y + img.getHeight() > other.img.getHeight())
            throw new IllegalArgumentException("Patch exceeds destination bounds.");

        Graphics2D g = other.img.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);                               // handles alpha channel :contentReference[oaicite:3]{index=3}
        g.drawImage(img, x, y, null);                                        // :contentReference[oaicite:4]{index=4}
        g.dispose();
    }

    /* ----------- annotate with text ----------- */
    public void putText(String txt, int x, int y, float fontSize,
                        Color color, int thickness /*unused in Java2D*/) {

        if (img == null) throw new IllegalStateException("Image not loaded.");

        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(color);
        g.setFont(img.getGraphics().getFont().deriveFont(fontSize * 12));     // simple scale
        g.drawString(txt, x, y);                                             // :contentReference[oaicite:5]{index=5}
        g.dispose();
    }

    /* ----------- display in a Swing window ----------- */
    public void show() {
        if (img == null) throw new IllegalStateException("Image not loaded.");

        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Image");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(new JLabel(new ImageIcon(img)));                            // :contentReference[oaicite:6]{index=6}
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
    public int getWidth() { return img.getWidth(); }
    public int getHeight() { return img.getHeight(); }

    /* ----------- access (optional) ----------- */
    public BufferedImage get() { return img; }
}