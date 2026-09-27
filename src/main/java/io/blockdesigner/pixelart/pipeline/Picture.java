package io.blockdesigner.pixelart.pipeline;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * An image as plain pixels: {@code width × height} {@code 0xAARRGGBB} values, row by row from the top left.
 * Immutable by convention: stages make new pictures instead of changing one.
 */
public record Picture(int width, int height, int[] argb) {

    public Picture {
        Objects.requireNonNull(argb, "argb");
        if (width <= 0 || height <= 0 || argb.length != width * height)
            throw new IllegalArgumentException("Bad picture size " + width + "×" + height + " for " + argb.length + " pixels");
    }

    /** Reads PNG, JPEG, BMP or GIF (the first frame). */
    public static Picture read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new IOException("Not an image BlockDesigner can read: " + file.getFileName());
            return of(img);
        }
    }

    public static Picture of(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        return new Picture(w, h, img.getRGB(0, 0, w, h, null, 0, w));
    }

    public int get(int x, int y) {
        return argb[y * width + x];
    }

    public int alpha(int x, int y) {
        return argb[y * width + x] >>> 24;
    }

    public BufferedImage toImage() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, argb, 0, width);
        return img;
    }
}
