package io.blockdesigner.pixelart.palette;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.pixelart.pipeline.LabImage;
import io.blockdesigner.pixelart.pipeline.OkLab;
import io.blockdesigner.pixelart.pipeline.Segmentation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The picture as blocks, still flat: one block (or nothing) per pixel of the prepared image.
 *
 * @param cells   index into {@code palette} per pixel, row by row from the top left; −1 for nothing
 * @param palette the blocks used
 * @param colours the colour each palette block shows (the matched face), OKLab
 */
public record BlockGrid(int width, int height, int[] cells, List<BlockState> palette, List<float[]> colours) {

    /** How pixels become blocks. */
    public enum Method {
        /** One block per region: clean shapes, like a poster. */
        REGIONS("Regions (clean shapes)"),
        /** The closest block to each pixel. */
        PIXELS("Pixels"),
        /** Pixels, spreading each one's colour error to its neighbours: smoother gradients. */
        DITHER_DIFFUSE("Pixels + dithering (smooth)"),
        /** Pixels, mixing the two closest blocks in a regular pattern. */
        DITHER_ORDERED("Pixels + dithering (pattern)");

        public final String label;

        Method(String label) {
            this.label = label;
        }

        public static Method of(String label) {
            for (Method m : values()) if (m.label.equals(label)) return m;
            return REGIONS;
        }
    }

    /** Most error (L, a, b) one pixel may take on from its neighbours while dithering. */
    private static final float[] MAX_CARRY = {0.12f, 0.06f, 0.06f};

    private static final int[] BAYER4 ={0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    public BlockState block(int x, int y) {
        int c = cells[y * width + x];
        return c < 0 ? null : palette.get(c);
    }

    /**
     * @param maxBlocks at most this many kinds of block (the most used ones); 0 for no limit
     */
    public static BlockGrid match(LabImage img, Segmentation seg, Matcher matcher, Method method, int maxBlocks) {
        int[] picked = pick(img, seg, matcher, method);
        if (maxBlocks > 0) {
            Map<Integer, Integer> uses = new LinkedHashMap<>();
            for (int p : picked) if (p >= 0) uses.merge(p, 1, Integer::sum);
            if (uses.size() > maxBlocks) {
                Set<BlockState> keep = new HashSet<>();
                Matcher all = matcher;
                uses.entrySet().stream().sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
                                .thenComparing(Map.Entry.comparingByKey()))
                        .limit(maxBlocks).forEach(e -> keep.add(all.get(e.getKey()).block()));
                matcher = matcher.only(keep);
                picked = pick(img, seg, matcher, method);
            }
        }
        dropHalo(img, seg.mask(), matcher, picked);
        // Compact into a palette of the blocks actually used, most used first.
        Map<Integer, Integer> count = new LinkedHashMap<>();
        for (int p : picked) if (p >= 0) count.merge(p, 1, Integer::sum);
        List<Integer> order = new ArrayList<>(count.keySet());
        order.sort(Comparator.comparing((Integer k) -> -count.get(k)).thenComparing(k -> k));
        int[] remap = new int[matcher.candidates().size()];
        List<BlockState> palette = new ArrayList<>();
        List<float[]> colours = new ArrayList<>();
        for (int k : order) {
            remap[k] = palette.size();
            palette.add(matcher.get(k).block());
            colours.add(matcher.get(k).colour());
        }
        int[] cells = new int[picked.length];
        for (int i = 0; i < cells.length; i++) cells[i] = picked[i] < 0 ? -1 : remap[picked[i]];
        return new BlockGrid(img.width(), img.height(), cells, List.copyOf(palette), List.copyOf(colours));
    }

    /** Candidate index per pixel. */
    private static int[] pick(LabImage img, Segmentation seg, Matcher matcher, Method method) {
        int w = img.width(), h = img.height(), n = img.size();
        int[] out = new int[n];
        switch (method) {
            case REGIONS -> {
                int[] perRegion = new int[seg.regions().length];
                for (int r = 0; r < perRegion.length; r++) perRegion[r] = matcher.nearest(seg.regions()[r].mean());
                for (int i = 0; i < n; i++) out[i] = seg.region()[i] < 0 ? -1 : perRegion[seg.region()[i]];
            }
            case PIXELS -> {
                for (int i = 0; i < n; i++) out[i] = seg.mask()[i] ? matcher.nearest(img.lab(i)) : -1;
            }
            case DITHER_DIFFUSE -> {
                // Floyd–Steinberg in OKLab, serpentine so the error doesn't drift one way.
                float[] err = img.lab().clone();
                for (int y = 0; y < h; y++) {
                    boolean ltr = (y & 1) == 0;
                    for (int s = 0; s < w; s++) {
                        int x = ltr ? s : w - 1 - s, i = y * w + x;
                        if (!seg.mask()[i]) {
                            out[i] = -1;
                            continue;
                        }
                        // The error carried in is capped: a colour no block comes close to (a dark olive with only
                        // concrete) would otherwise pile up error and burst out as specks of far-off blocks.
                        float[] want = new float[3];
                        for (int d = 0; d < 3; d++) {
                            float orig = img.lab()[3 * i + d];
                            want[d] = orig + Math.clamp(err[3 * i + d] - orig, -MAX_CARRY[d], MAX_CARRY[d]);
                        }
                        int best = matcher.nearest(want);
                        out[i] = best;
                        float[] got = matcher.get(best).colour();
                        int dx = ltr ? 1 : -1;
                        for (int d = 0; d < 3; d++) {
                            float e = (want[d] - got[d]) * 0.85f;
                            spread(err, seg.mask(), w, h, x + dx, y, d, e * 7 / 16f);
                            spread(err, seg.mask(), w, h, x - dx, y + 1, d, e * 3 / 16f);
                            spread(err, seg.mask(), w, h, x, y + 1, d, e * 5 / 16f);
                            spread(err, seg.mask(), w, h, x + dx, y + 1, d, e / 16f);
                        }
                    }
                }
            }
            case DITHER_ORDERED -> {
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        int i = y * w + x;
                        if (!seg.mask()[i]) {
                            out[i] = -1;
                            continue;
                        }
                        float[] p = img.lab(i);
                        int[] two = matcher.nearestTwo(p);
                        float[] c1 = matcher.get(two[0]).colour(), c2 = matcher.get(two[1]).colour();
                        // Where the pixel sits between the two blocks' colours, 0 at the first, 1 at the second.
                        float len = OkLab.dist2(c1, c2), t = 0;
                        if (len > 0) {
                            for (int d = 0; d < 3; d++) t += (p[d] - c1[d]) * (c2[d] - c1[d]);
                            t = Math.clamp(t / len, 0f, 1f);
                        }
                        float threshold = (BAYER4[(y & 3) * 4 + (x & 3)] + 0.5f) / 16f;
                        out[i] = t > threshold ? two[1] : two[0];
                    }
            }
        }
        return out;
    }

    /**
     * Scaling blends the subject's edge with the backdrop, and those in-between pixels can match the backdrop's own
     * block, leaving a thin outline of background colour around the subject. Edge cells whose block is the one the
     * neighbouring (opaque) backdrop would get are removed.
     */
    private static void dropHalo(LabImage img, boolean[] mask, Matcher matcher, int[] picked) {
        int w = img.width(), h = img.height();
        // The backdrop's average colour stands in for what lies beyond the picture's edge.
        double[] sum = new double[3];
        int n = 0;
        for (int i = 0; i < mask.length; i++)
            if (!mask[i] && img.alpha()[i] >= 128) {
                for (int d = 0; d < 3; d++) sum[d] += img.lab()[3 * i + d];
                n++;
            }
        int backdrop = n == 0 ? -1 : matcher.nearest(new float[]{(float) (sum[0] / n), (float) (sum[1] / n), (float) (sum[2] / n)});
        List<Integer> drop = new ArrayList<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (picked[i] < 0) continue;
                boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                if (edge && picked[i] == backdrop) {
                    drop.add(i);
                    continue;
                }
                search:
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int q = ny * w + nx;
                        if (mask[q] || img.alpha()[q] < 128) continue;
                        if (matcher.nearest(img.lab(q)) == picked[i]) {
                            drop.add(i);
                            break search;
                        }
                    }
            }
        for (int i : drop) picked[i] = -1;
    }

    private static void spread(float[] err, boolean[] mask, int w, int h, int x, int y, int d, float e) {
        if (x < 0 || x >= w || y >= h) return;
        int i = y * w + x;
        if (mask[i]) err[3 * i + d] += e;
    }

    /** How many cells use each block, in palette order. */
    public long[] counts() {
        long[] out = new long[palette.size()];
        for (int c : cells) if (c >= 0) out[c]++;
        return out;
    }

    /** For previews: each cell painted with its block's colour, empty cells transparent. */
    public int[] toArgb() {
        int[] rgb = new int[colours.size()];
        for (int k = 0; k < rgb.length; k++) rgb[k] = OkLab.toArgb(colours.get(k));
        int[] out = new int[cells.length];
        for (int i = 0; i < out.length; i++) out[i] = cells[i] < 0 ? 0 : rgb[cells[i]];
        return out;
    }
}
