package io.blockdesigner.pixelart.shape;

import io.blockdesigner.pixelart.pipeline.LabImage;
import io.blockdesigner.pixelart.pipeline.Segmentation;

/**
 * A relief: each pixel becomes a column whose height comes from the picture (its brightness, its colour region's
 * brightness, or a separate depth image). Heights are worked out at half-block steps and between block centres, so
 * slopes come out as stairs and half steps as slabs.
 */
public final class ReliefShape {

    /** Where heights come from. */
    public enum Source {
        BRIGHTNESS("Brightness"), REGIONS("Colour regions (layers)"), DEPTH_IMAGE("Depth image");

        public final String label;

        Source(String label) {
            this.label = label;
        }

        public static Source of(String label) {
            for (Source s : values()) if (s.label.equals(label)) return s;
            return BRIGHTNESS;
        }
    }

    /**
     * @param source    where heights come from
     * @param invert    dark is high instead of bright
     * @param maxHeight the highest point, in blocks
     * @param base      every pixel at least this many blocks high
     * @param smoothing blur radius in pixels (0 for none)
     * @param terraces  heights snap to this many levels (0 for smooth)
     * @param hollow    only a one-block shell under the surface
     */
    public record Settings(Source source, boolean invert, int maxHeight, int base, int smoothing, int terraces, boolean hollow) {
    }

    private ReliefShape() {
    }

    /**
     * @param depth a depth image already at the picture's size (lightness used), or null
     * @param fine  heights in half blocks, smoothed between block centres (for slabs and stairs); whole blocks when false
     */
    public static HalfField field(LabImage img, Segmentation seg, LabImage depth, Settings s, boolean fine) {
        int w = img.width(), h = img.height();
        boolean[] mask = seg.mask();
        float[] value = values(img, seg, depth, s.source());
        normalise(value, mask);
        if (s.smoothing() > 0) blur(value, mask, w, h, s.smoothing());
        for (int i = 0; i < value.length; i++) {
            float v = value[i];
            if (s.terraces() > 1) v = Math.round(v * (s.terraces() - 1)) / (float) (s.terraces() - 1);
            if (s.invert()) v = 1 - v;
            value[i] = v;
        }
        int base = Math.min(s.base(), s.maxHeight());
        float[] height = new float[w * h];
        for (int i = 0; i < height.length; i++) height[i] = mask[i] ? base + (s.maxHeight() - base) * value[i] : 0;

        // Heights of the four quarter-columns of each pixel, in half blocks.
        int w2 = 2 * w, h2 = 2 * h;
        int[] top = new int[w2 * h2];
        int deepest = 0;
        for (int sv = 0; sv < h2; sv++)
            for (int su = 0; su < w2; su++) {
                int u = su / 2, v = sv / 2;
                if (!mask[v * w + u]) continue;
                double hh;
                if (fine) {
                    // Quarter centres sit a quarter block from the pixel centre, towards a neighbour.
                    int nu = su % 2 == 0 ? u - 1 : u + 1, nv = sv % 2 == 0 ? v - 1 : v + 1;
                    hh = bilinear(height, mask, w, h, u, v, nu, nv);
                    top[sv * w2 + su] = Math.max(1, (int) Math.round(hh * 2));
                } else {
                    top[sv * w2 + su] = Math.max(2, 2 * Math.round(height[v * w + u]));
                }
                deepest = Math.max(deepest, top[sv * w2 + su]);
            }
        int depthBlocks = Math.max(1, (deepest + 1) / 2);
        int shell = s.hollow() ? 2 : Integer.MAX_VALUE;

        return new HalfField() {
            @Override
            public int width() {
                return w;
            }

            @Override
            public int height() {
                return h;
            }

            @Override
            public int depth() {
                return depthBlocks;
            }

            @Override
            public boolean filled(int su, int sv, int sd) {
                int t = top[sv * w2 + su];
                return sd < t && sd >= t - shell;
            }

            @Override
            public int columnDepth(int u, int v) {
                int t = 0;
                for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) t = Math.max(t, top[(2 * v + b) * w2 + 2 * u + a]);
                return (t + 1) / 2;
            }
        };
    }

    /** 0 … 1-ish per pixel before normalising. */
    private static float[] values(LabImage img, Segmentation seg, LabImage depth, Source source) {
        int n = img.size();
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = switch (source) {
                case BRIGHTNESS -> img.lab()[3 * i];
                case REGIONS -> seg.region()[i] < 0 ? 0 : seg.regions()[seg.region()[i]].mean()[0];
                case DEPTH_IMAGE -> depth == null ? img.lab()[3 * i] : depth.lab()[3 * i];
            };
        }
        return out;
    }

    /** Stretches the subject's values to 0 … 1 (all 1 when they are all the same). */
    static void normalise(float[] v, boolean[] mask) {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int i = 0; i < v.length; i++)
            if (mask[i]) {
                lo = Math.min(lo, v[i]);
                hi = Math.max(hi, v[i]);
            }
        for (int i = 0; i < v.length; i++) v[i] = !mask[i] ? 0 : hi - lo < 1e-6f ? 1 : (v[i] - lo) / (hi - lo);
    }

    /** Two box blurs (close to a Gaussian) that only mix subject pixels with each other. */
    static void blur(float[] v, boolean[] mask, int w, int h, int r) {
        for (int pass = 0; pass < 2; pass++) {
            float[] tmp = new float[v.length];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) tmp[y * w + x] = boxMean(v, mask, w, h, x, y, r, 0, y * w + x);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) v[y * w + x] = boxMean(tmp, mask, w, h, x, y, 0, r, y * w + x);
        }
    }

    private static float boxMean(float[] v, boolean[] mask, int w, int h, int x, int y, int rx, int ry, int self) {
        if (!mask[self]) return 0;
        double sum = 0;
        int n = 0;
        for (int dy = -ry; dy <= ry; dy++)
            for (int dx = -rx; dx <= rx; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= w || yy >= h || !mask[yy * w + xx]) continue;
                sum += v[yy * w + xx];
                n++;
            }
        return (float) (sum / n);
    }

    /**
     * The height a quarter of the way from pixel (u, v) towards the diagonal neighbour (nu, nv), mixing in only
     * neighbours that are part of the subject (the edge of the subject doesn't slope down to nothing).
     */
    private static double bilinear(float[] height, boolean[] mask, int w, int h, int u, int v, int nu, int nv) {
        double self = height[v * w + u];
        double hu = at(height, mask, w, h, nu, v, self);
        double hv = at(height, mask, w, h, u, nv, self);
        // No diagonal neighbour (the picture's edge, or backdrop): continue from the side that exists, so the edge
        // row slopes like the row next to it instead of bending towards its own height.
        boolean hasU = inside(mask, w, h, nu, v), hasV = inside(mask, w, h, u, nv);
        double huv = inside(mask, w, h, nu, nv) ? height[nv * w + nu] : hasU && !hasV ? hu : hasV && !hasU ? hv : hu + hv - self;
        // Weights 3/4 towards self on each axis.
        return 0.5625 * self + 0.1875 * hu + 0.1875 * hv + 0.0625 * huv;
    }

    private static double at(float[] height, boolean[] mask, int w, int h, int u, int v, double fallback) {
        return inside(mask, w, h, u, v) ? height[v * w + u] : fallback;
    }

    private static boolean inside(boolean[] mask, int w, int h, int u, int v) {
        return u >= 0 && v >= 0 && u < w && v < h && mask[v * w + u];
    }
}
