package io.blockdesigner.pixelart.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The image split into regions: connected patches of pixels whose colours fell into the same {@link KMeans} cluster.
 * Patches smaller than a minimum size are merged into the neighbour with the closest colour, which removes speckle
 * (JPEG noise, anti-aliased edges) and leaves the shapes a person would outline.
 *
 * @param mask    true for pixels to build (not background)
 * @param region  each pixel's region, −1 for background
 * @param regions the regions, indexed by the numbers in {@code region}
 */
public record Segmentation(int width, int height, boolean[] mask, int[] region, Region[] regions) {

    /**
     * One region.
     *
     * @param cluster the colour cluster it came from
     * @param area    pixels in it
     * @param mean    its average OKLab colour
     */
    public record Region(int cluster, int area, float[] mean) {
    }

    /**
     * @param img     the prepared image
     * @param mask    the pixels to build
     * @param colours how many colour clusters (k)
     * @param minArea regions smaller than this many pixels merge into a neighbour (1 keeps everything)
     */
    public static Segmentation run(LabImage img, boolean[] mask, int colours, int minArea, long seed) {
        KMeans.Result km = KMeans.run(img, mask, colours, seed);
        int w = img.width(), h = img.height(), n = img.size();
        int[] cluster = km.assignment();

        // Connected components of equal cluster (4-neighbours).
        int[] comp = new int[n];
        Arrays.fill(comp, -1);
        List<Integer> compCluster = new ArrayList<>();
        int[] stack = new int[n];
        for (int s = 0; s < n; s++) {
            if (cluster[s] < 0 || comp[s] >= 0) continue;
            int id = compCluster.size();
            compCluster.add(cluster[s]);
            int top = 0;
            stack[top++] = s;
            comp[s] = id;
            while (top > 0) {
                int i = stack[--top];
                int x = i % w, y = i / w;
                if (x > 0) top = push(cluster, comp, stack, top, i - 1, cluster[s], id);
                if (x < w - 1) top = push(cluster, comp, stack, top, i + 1, cluster[s], id);
                if (y > 0) top = push(cluster, comp, stack, top, i - w, cluster[s], id);
                if (y < h - 1) top = push(cluster, comp, stack, top, i + w, cluster[s], id);
            }
        }
        int m = compCluster.size();

        // Per component: area, colour sums, pixel lists.
        int[] area = new int[m];
        double[] sum = new double[m * 3];
        for (int i = 0; i < n; i++) {
            int c = comp[i];
            if (c < 0) continue;
            area[c]++;
            for (int d = 0; d < 3; d++) sum[3 * c + d] += img.lab()[3 * i + d];
        }
        int[][] pixels = new int[m][];
        int[] fill = new int[m];
        for (int c = 0; c < m; c++) pixels[c] = new int[area[c]];
        for (int i = 0; i < n; i++) if (comp[i] >= 0) pixels[comp[i]][fill[comp[i]]++] = i;

        int[] parent = new int[m];
        for (int c = 0; c < m; c++) parent[c] = c;
        int[] ownCluster = compCluster.stream().mapToInt(Integer::intValue).toArray();

        if (minArea > 1) {
            Integer[] order = new Integer[m];
            for (int c = 0; c < m; c++) order[c] = c;
            Arrays.sort(order, (a, b) -> Integer.compare(area[a], area[b]));
            for (int r : order) {
                if (find(parent, r) != r || area[r] >= minArea) continue;
                float[] mean = mean(sum, area, r);
                int best = -1;
                float bestD = Float.MAX_VALUE;
                for (int k = 0; k < fill[r]; k++) {
                    int i = pixels[r][k], x = i % w, y = i / w;
                    int[] nb = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
                    for (int q : nb) {
                        if (q < 0 || comp[q] < 0) continue;
                        int o = find(parent, comp[q]);
                        if (o == r) continue;
                        float d = OkLab.dist2(mean, mean(sum, area, o));
                        if (d < bestD || (d == bestD && area[o] > area[best])) {
                            bestD = d;
                            best = o;
                        }
                    }
                }
                if (best < 0) continue; // an island on its own: keep it
                parent[r] = best;
                area[best] += area[r];
                for (int d = 0; d < 3; d++) sum[3 * best + d] += sum[3 * r + d];
                if (pixels[best].length < fill[best] + fill[r]) pixels[best] = Arrays.copyOf(pixels[best], Math.max(fill[best] + fill[r], pixels[best].length * 2));
                System.arraycopy(pixels[r], 0, pixels[best], fill[best], fill[r]);
                fill[best] += fill[r];
            }
        }

        // Number the surviving regions 0…
        int[] number = new int[m];
        Arrays.fill(number, -1);
        List<Region> regions = new ArrayList<>();
        for (int c = 0; c < m; c++) {
            if (find(parent, c) != c) continue;
            number[c] = regions.size();
            regions.add(new Region(ownCluster[c], area[c], mean(sum, area, c)));
        }
        int[] region = new int[n];
        for (int i = 0; i < n; i++) region[i] = comp[i] < 0 ? -1 : number[find(parent, comp[i])];
        return new Segmentation(w, h, mask, region, regions.toArray(Region[]::new));
    }

    private static int push(int[] cluster, int[] comp, int[] stack, int top, int q, int want, int id) {
        if (comp[q] < 0 && cluster[q] == want) {
            comp[q] = id;
            stack[top++] = q;
        }
        return top;
    }

    private static int find(int[] parent, int c) {
        while (parent[c] != c) {
            parent[c] = parent[parent[c]];
            c = parent[c];
        }
        return c;
    }

    private static float[] mean(double[] sum, int[] area, int c) {
        return new float[]{(float) (sum[3 * c] / area[c]), (float) (sum[3 * c + 1] / area[c]), (float) (sum[3 * c + 2] / area[c])};
    }

    /** For previews: each pixel painted with its region's average colour, background transparent. */
    public int[] toArgb() {
        int[] out = new int[width * height];
        int[] colour = new int[regions.length];
        for (int r = 0; r < regions.length; r++) colour[r] = OkLab.toArgb(regions[r].mean());
        for (int i = 0; i < out.length; i++) out[i] = region[i] < 0 ? 0 : colour[region[i]];
        return out;
    }
}
