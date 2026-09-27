package io.blockdesigner.pixelart.shape;

/**
 * How far each cell of a shape is from its edge, exactly (Euclidean), with the two-pass method of Felzenszwalb and
 * Huttenlocher. Everything outside the grid counts as outside the shape.
 */
public final class DistanceTransform {
    private static final double INF = 1e20;

    private DistanceTransform() {
    }

    /**
     * @param inside {@code w × h} flags, row by row
     * @return per cell, the distance from its centre to the centre of the nearest cell outside the shape (0 outside)
     */
    public static double[] of(boolean[] inside, int w, int h) {
        // Pad by one cell so the grid's border counts as outside.
        int pw = w + 2, ph = h + 2;
        double[] f = new double[pw * ph];
        for (int y = 0; y < ph; y++)
            for (int x = 0; x < pw; x++) {
                boolean in = x > 0 && y > 0 && x <= w && y <= h && inside[(y - 1) * w + (x - 1)];
                f[y * pw + x] = in ? INF : 0;
            }
        double[] col = new double[Math.max(pw, ph)], out = new double[Math.max(pw, ph)];
        for (int x = 0; x < pw; x++) {
            for (int y = 0; y < ph; y++) col[y] = f[y * pw + x];
            pass(col, ph, out);
            for (int y = 0; y < ph; y++) f[y * pw + x] = out[y];
        }
        for (int y = 0; y < ph; y++) {
            System.arraycopy(f, y * pw, col, 0, pw);
            pass(col, pw, out);
            System.arraycopy(out, 0, f, y * pw, pw);
        }
        double[] d = new double[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) d[y * w + x] = Math.sqrt(f[(y + 1) * pw + x + 1]);
        return d;
    }

    /** One-dimensional squared distance transform (lower envelope of parabolas). */
    private static void pass(double[] f, int n, double[] d) {
        int[] v = new int[n];
        double[] z = new double[n + 1];
        int k = 0;
        v[0] = 0;
        z[0] = -INF;
        z[1] = INF;
        for (int q = 1; q < n; q++) {
            double s;
            while (true) {
                s = ((f[q] + (double) q * q) - (f[v[k]] + (double) v[k] * v[k])) / (2.0 * q - 2.0 * v[k]);
                if (s <= z[k] && k > 0) k--;
                else break;
            }
            if (s <= z[k]) {
                // k == 0 and the new parabola beats it everywhere
                v[0] = q;
                z[0] = -INF;
                z[1] = INF;
                continue;
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = INF;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) k++;
            double dq = q - v[k];
            d[q] = dq * dq + f[v[k]];
        }
    }
}
