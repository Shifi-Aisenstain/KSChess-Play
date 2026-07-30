package graphics;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Image {

    private BufferedImage img;

    public Image read(String path,
                      Dimension targetSize,
                      boolean keepAspect,
                      Object interpolation ) {

        try {
            img = ImageIO.read(new File(path));
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load image: " + path);
        }
        if (img == null) throw new IllegalArgumentException("Unsupported image: " + path);

        if (targetSize != null) {
            int tw = targetSize.width, th = targetSize.height;
            int w = img.getWidth(), h = img.getHeight();

            int nw, nh;
            if (keepAspect) {
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
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();
            img = dst;
        }
        return this;
    }

    public Image read(String path) { return read(path, null, false, null); }

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

    public void fillRectAlpha(int x, int y, int w, int h, Color color) {
        if (img == null) throw new IllegalStateException("Image not loaded.");

        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(x, y, w, h);
        g.dispose();
    }

    public void drawOn(Image other, int x, int y) {
        if (img == null || other.img == null)
            throw new IllegalStateException("Both images must be loaded.");

        if (x + img.getWidth()  > other.img.getWidth()
                || y + img.getHeight() > other.img.getHeight())
            throw new IllegalArgumentException("Patch exceeds destination bounds.");

        Graphics2D g = other.img.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(img, x, y, null);
        g.dispose();
    }

    public void putText(String txt, int x, int y, float fontSize,
                        Color color, int thickness ) {

        if (img == null) throw new IllegalStateException("Image not loaded.");

        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(color);
        g.setFont(img.getGraphics().getFont().deriveFont(fontSize * 12));
        g.drawString(txt, x, y);
        g.dispose();
    }

    public void show() {
        if (img == null) throw new IllegalStateException("Image not loaded.");

        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Image");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(new JLabel(new ImageIcon(img)));
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
    public int getWidth() { return img.getWidth(); }
    public int getHeight() { return img.getHeight(); }

    public BufferedImage get() { return img; }
}
