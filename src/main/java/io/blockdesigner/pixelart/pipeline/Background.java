package io.blockdesigner.pixelart.pipeline;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Works out which pixels are the subject and which are backdrop, so the backdrop isn't built. Without AI this relies
 * on two things pictures usually have: transparency, or a plain background colour that touches the border.
 */
public final class Background {

    public enum Mode {
        /** Transparency when the picture has some, else the border colour when the border is mostly one colour, else nothing. */
        AUTO,
        /** Pixels that are mostly transparent. */
        TRANSPARENT,
        /** The colour around the border, filled inwards from the edges. */
        BORDER,
        /** Build every pixel. */
        NONE
    }

    private Background() {
    }

    /**
     * @param tolerance how far (OKLab distance) a pixel may be from the border colour and still count as background
     * @return true for each pixel to build
     */
    public static boolean[] mask(LabImage img, Mode mode, double tolerance) {
        return switch (mode) {
            case NONE -> all(img.size());
            case TRANSPARENT -> opaque(img);
            case BORDER -> border(img, tolerance, opaque(img));
            case AUTO -> {
                boolean[] opaque = opaque(img);
                int clear = 0;
                for (boolean o : opaque) if (!o) clear++;
                if (clear > img.size() / 100) yield opaque;
                if (!borderIsPlain(img, tolerance)) yield opaque;
                // A picture that is (nearly) all border colour has no subject to cut out: keep it whole.
                boolean[] cut = border(img, tolerance, opaque);
                int kept = 0;
                for (boolean k : cut) if (k) kept++;
                yield kept >= Math.max(1, img.size() / 50) ? cut : opaque;
            }
        };
    }

    private static boolean[] all(int n) {
        boolean[] m = new boolean[n];
        Arrays.fill(m, true);
        return m;
    }

    private static boolean[] opaque(LabImage img) {
        boolean[] m = new boolean[img.size()];
        for (int i = 0; i < m.length; i++) m[i] = img.alpha()[i] >= 128;
        return m;
    }

    /** The border's typical colour: the per-channel median of the border pixels. */
    static float[] borderColour(LabImage img) {
        int[] idx = borderPixels(img);
        float[] out = new float[3];
        float[] ch = new float[idx.length];
        for (int d = 0; d < 3; d++) {
            for (int i = 0; i < idx.length; i++) ch[i] = img.lab()[3 * idx[i] + d];
            Arrays.sort(ch);
            out[d] = ch[ch.length / 2];
        }
        return out;
    }

    /** At least 60% of the border within tolerance of its median colour. */
    static boolean borderIsPlain(LabImage img, double tolerance) {
        float[] ref = borderColour(img);
        int[] idx = borderPixels(img);
        float t2 = (float) (tolerance * tolerance);
        int near = 0;
        for (int i : idx) if (OkLab.dist2(ref, img.lab()[3 * i], img.lab()[3 * i + 1], img.lab()[3 * i + 2]) <= t2) near++;
        return near >= idx.length * 0.6;
    }

    /** Floods in from the border through pixels close to the border colour; everything reached is background. */
    private static boolean[] border(LabImage img, double tolerance, boolean[] opaque) {
        int w = img.width(), h = img.height();
        float[] ref = borderColour(img);
        float t2 = (float) (tolerance * tolerance);
        boolean[] keep = opaque.clone();
        boolean[] seen = new boolean[img.size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i : borderPixels(img)) {
            seen[i] = true;
            queue.add(i);
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            boolean bg = !opaque[i] || OkLab.dist2(ref, img.lab()[3 * i], img.lab()[3 * i + 1], img.lab()[3 * i + 2]) <= t2;
            if (!bg) continue;
            keep[i] = false;
            int x = i % w, y = i / w;
            if (x > 0 && !seen[i - 1]) visit(seen, queue, i - 1);
            if (x < w - 1 && !seen[i + 1]) visit(seen, queue, i + 1);
            if (y > 0 && !seen[i - w]) visit(seen, queue, i - w);
            if (y < h - 1 && !seen[i + w]) visit(seen, queue, i + w);
        }
        return keep;
    }

    private static void visit(boolean[] seen, ArrayDeque<Integer> queue, int i) {
        seen[i] = true;
        queue.add(i);
    }

    private static int[] borderPixels(LabImage img) {
        int w = img.width(), h = img.height();
        if (w == 1 || h == 1) {
            int[] all = new int[w * h];
            for (int i = 0; i < all.length; i++) all[i] = i;
            return all;
        }
        int[] out = new int[2 * w + 2 * (h - 2)];
        int n = 0;
        for (int x = 0; x < w; x++) {
            out[n++] = x;
            out[n++] = (h - 1) * w + x;
        }
        for (int y = 1; y < h - 1; y++) {
            out[n++] = y * w;
            out[n++] = y * w + w - 1;
        }
        return out;
    }
}
