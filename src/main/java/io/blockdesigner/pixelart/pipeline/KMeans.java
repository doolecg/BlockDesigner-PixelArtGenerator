package io.blockdesigner.pixelart.pipeline;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Groups the colours of an image into {@code k} clusters in OKLab (k-means with k-means++ seeding). This is what
 * "seeing the hues" means for the builder: each cluster is one of the image's main colours.
 *
 * <p>Works on the distinct colours with their counts rather than on every pixel, so pixel art and flat logos (a
 * handful of colours) cluster instantly. The seed is fixed, so the same image and settings always give the same
 * clusters.
 */
public final class KMeans {

    /**
     * @param centres    {@code k × 3} OKLab centres (fewer than asked when the image has fewer distinct colours)
     * @param assignment the cluster of each input pixel, −1 for pixels that were left out
     */
    public record Result(float[][] centres, int[] assignment) {
        public int k() {
            return centres.length;
        }
    }

    private KMeans() {
    }

    /**
     * @param img     the image
     * @param include which pixels take part (null for all)
     * @param k       how many clusters, at least 1
     */
    public static Result run(LabImage img, boolean[] include, int k, long seed) {
        int n = img.size();
        // Distinct colours (exactly equal floats), with how many pixels have each.
        Map<Key, Integer> index = new HashMap<>();
        float[] pts = new float[n * 3];
        double[] wts = new double[n];
        int[] pixelToPoint = new int[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (include != null && !include[i]) {
                pixelToPoint[i] = -1;
                continue;
            }
            Key key = new Key(img.lab()[3 * i], img.lab()[3 * i + 1], img.lab()[3 * i + 2]);
            Integer at = index.get(key);
            if (at == null) {
                at = m++;
                index.put(key, at);
                System.arraycopy(img.lab(), 3 * i, pts, 3 * at, 3);
            }
            wts[at]++;
            pixelToPoint[i] = at;
        }
        if (m == 0) return new Result(new float[0][], filled(n));
        k = Math.clamp(k, 1, m);

        float[][] centres = seed(pts, wts, m, k, new SplittableRandom(seed));
        k = centres.length;
        int[] label = new int[m];
        double[] sum = new double[k * 3];
        double[] count = new double[k];
        for (int iter = 0; iter < 40; iter++) {
            boolean changed = iter == 0;
            for (int p = 0; p < m; p++) {
                int best = nearest(centres, pts[3 * p], pts[3 * p + 1], pts[3 * p + 2]);
                if (best != label[p]) {
                    label[p] = best;
                    changed = true;
                }
            }
            if (!changed) break;
            java.util.Arrays.fill(sum, 0);
            java.util.Arrays.fill(count, 0);
            for (int p = 0; p < m; p++) {
                int c = label[p];
                for (int d = 0; d < 3; d++) sum[3 * c + d] += pts[3 * p + d] * wts[p];
                count[c] += wts[p];
            }
            for (int c = 0; c < k; c++)
                if (count[c] > 0) for (int d = 0; d < 3; d++) centres[c][d] = (float) (sum[3 * c + d] / count[c]);
        }

        // Clusters that ended up empty are dropped, and the rest renumbered.
        int[] remap = new int[k];
        int kept = 0;
        boolean[] used = new boolean[k];
        for (int p = 0; p < m; p++) used[label[p]] = true;
        for (int c = 0; c < k; c++) remap[c] = used[c] ? kept++ : -1;
        float[][] out = new float[kept][];
        for (int c = 0; c < k; c++) if (used[c]) out[remap[c]] = centres[c];

        int[] assignment = new int[n];
        for (int i = 0; i < n; i++) assignment[i] = pixelToPoint[i] < 0 ? -1 : remap[label[pixelToPoint[i]]];
        return new Result(out, assignment);
    }

    /** k-means++: each next centre is picked with probability proportional to its weighted squared distance. */
    private static float[][] seed(float[] pts, double[] wts, int m, int k, SplittableRandom rnd) {
        float[][] centres = new float[k][];
        // The first centre: the most common colour, so the result doesn't depend on luck for flat images.
        int first = 0;
        for (int p = 1; p < m; p++) if (wts[p] > wts[first]) first = p;
        centres[0] = point(pts, first);
        double[] d2 = new double[m];
        for (int p = 0; p < m; p++) d2[p] = OkLab.dist2(centres[0], pts[3 * p], pts[3 * p + 1], pts[3 * p + 2]);
        for (int c = 1; c < k; c++) {
            double total = 0;
            for (int p = 0; p < m; p++) total += d2[p] * wts[p];
            int pick = 0;
            if (total <= 0) break;
            double r = rnd.nextDouble() * total;
            for (int p = 0; p < m; p++) {
                r -= d2[p] * wts[p];
                if (r <= 0) {
                    pick = p;
                    break;
                }
            }
            centres[c] = point(pts, pick);
            for (int p = 0; p < m; p++)
                d2[p] = Math.min(d2[p], OkLab.dist2(centres[c], pts[3 * p], pts[3 * p + 1], pts[3 * p + 2]));
        }
        // Fewer distinct colours than spread (every remaining distance 0): stop at the centres found.
        int found = 0;
        while (found < k && centres[found] != null) found++;
        if (found < k) centres = java.util.Arrays.copyOf(centres, found);
        return centres;
    }

    static int nearest(float[][] centres, float l, float a, float b) {
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int c = 0; c < centres.length; c++) {
            float d = OkLab.dist2(centres[c], l, a, b);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    private static float[] point(float[] pts, int p) {
        return new float[]{pts[3 * p], pts[3 * p + 1], pts[3 * p + 2]};
    }

    private static int[] filled(int n) {
        int[] a = new int[n];
        java.util.Arrays.fill(a, -1);
        return a;
    }

    private record Key(float l, float a, float b) {
    }
}
