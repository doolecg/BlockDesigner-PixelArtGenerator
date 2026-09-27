package io.blockdesigner.pixelart.shape;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.pixelart.palette.BlockGrid;

/**
 * Solids from a picture's outline (its background cut away), guessed without AI:
 * <ul>
 *   <li><b>extrude</b>: the outline pushed out to a thickness, with an optional bevel on the front edge;</li>
 *   <li><b>inflate</b>: the outline blown up like a balloon, thickest far from its edges;</li>
 *   <li><b>revolve</b>: each row spun round a vertical axis, for vases, bottles, towers and trees.</li>
 * </ul>
 * Worked out at half-block steps, so edges and curves come out in slabs and stairs.
 */
public final class SolidShapes {

    private SolidShapes() {
    }

    /** The outline at half-block resolution: each pixel as 2×2 cells. */
    static boolean[] halfMask(boolean[] mask, int w, int h) {
        int w2 = 2 * w;
        boolean[] out = new boolean[w2 * 2 * h];
        for (int sv = 0; sv < 2 * h; sv++)
            for (int su = 0; su < w2; su++) out[sv * w2 + su] = mask[(sv / 2) * w + su / 2];
        return out;
    }

    /**
     * @param thickness how deep, in blocks
     * @param bevel     how far in (in half blocks) the front face starts sloping back at the edges; 0 for square edges
     */
    public static HalfField extrude(boolean[] mask, int w, int h, int thickness, int bevel) {
        int w2 = 2 * w;
        boolean[] m = halfMask(mask, w, h);
        double[] dist = bevel > 0 ? DistanceTransform.of(m, w2, 2 * h) : null;
        int full = 2 * thickness;
        int[] front = new int[m.length];
        for (int i = 0; i < m.length; i++) {
            if (!m[i]) continue;
            // Within `bevel` of the edge the front steps back one half per half, down to half the thickness.
            int back = dist == null ? 0 : (int) Math.max(0, Math.ceil(bevel - dist[i] + 1));
            front[i] = Math.max(Math.max(1, full / 2), full - back);
        }
        return columns(front, null, w, h, thickness);
    }

    /**
     * @param puffiness 1 blows a disc up into a ball; lower is flatter, higher rounder
     */
    public static HalfField inflate(boolean[] mask, int w, int h, double puffiness) {
        int w2 = 2 * w;
        boolean[] m = halfMask(mask, w, h);
        double[] dist = DistanceTransform.of(m, w2, 2 * h);
        double r = 0;
        for (double d : dist) r = Math.max(r, d);
        // Half the thickness at each cell: a circle's profile, sqrt(d·(2R − d)), so a disc becomes a ball.
        double[] half = new double[m.length];
        double most = 0;
        for (int i = 0; i < m.length; i++) {
            if (!m[i]) continue;
            double d = Math.min(dist[i] - 0.5, r);
            half[i] = puffiness * Math.sqrt(Math.max(0, d * (2 * r - d)));
            most = Math.max(most, half[i]);
        }
        int depth = Math.max(1, (int) Math.ceil(most)); // blocks: 2·most halves across
        double centre = depth; // the middle, in halves
        int[] lo = new int[m.length], hi = new int[m.length];
        for (int i = 0; i < m.length; i++) {
            if (!m[i]) continue;
            // At least one half thick wherever the outline is, so thin parts don't vanish.
            double t = Math.max(0.5, half[i]);
            lo[i] = (int) Math.round(centre - t);
            hi[i] = Math.max(lo[i] + 1, (int) Math.round(centre + t));
        }
        return columns(hi, lo, w, h, depth);
    }

    /**
     * Each row's outline spun round a vertical axis through the middle of the outline. Colours wrap round: a cell
     * takes the colour the picture has at the same distance from the axis.
     */
    public static HalfField revolve(boolean[] mask, int w, int h) {
        int w2 = 2 * w, h2 = 2 * h;
        boolean[] m = halfMask(mask, w, h);
        int minU = Integer.MAX_VALUE, maxU = -1;
        for (int sv = 0; sv < h2; sv++)
            for (int su = 0; su < w2; su++)
                if (m[sv * w2 + su]) {
                    minU = Math.min(minU, su);
                    maxU = Math.max(maxU, su);
                }
        if (maxU < 0) return columns(new int[m.length], null, w, h, 1);
        double axis = (minU + maxU + 1) / 2.0; // in halves
        // Each half-row's radius: the farthest outline cell from the axis.
        double[] radius = new double[h2];
        for (int sv = 0; sv < h2; sv++)
            for (int su = 0; su < w2; su++)
                if (m[sv * w2 + su]) radius[sv] = Math.max(radius[sv], Math.abs(su + 0.5 - axis) + 0.5);
        double biggest = 0;
        for (double r : radius) biggest = Math.max(biggest, r);
        int depth = Math.max(1, (int) Math.ceil(biggest)); // blocks: 2·biggest halves across
        double depthAxis = depth; // in halves
        double axisBlocks = axis / 2, depthAxisBlocks = depthAxis / 2;
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
                return depth;
            }

            @Override
            public boolean filled(int su, int sv, int sd) {
                double dx = su + 0.5 - axis, dz = sd + 0.5 - depthAxis;
                return dx * dx + dz * dz <= radius[sv] * radius[sv];
            }

            @Override
            public BlockState material(BlockGrid grid, int u, int v, int k) {
                // The pixel as far from the axis as this cell, on the right of it if that's in the outline, else the left.
                double rho = Math.hypot(u + 0.5 - axisBlocks, k + 0.5 - depthAxisBlocks);
                int right = (int) Math.floor(axisBlocks + rho), left = (int) Math.floor(axisBlocks - rho);
                for (int du = 0; du <= 1; du++) {
                    for (int c : new int[]{right - du, left + du}) {
                        if (c < 0 || c >= w) continue;
                        BlockState b = grid.block(c, v);
                        if (b != null) return b;
                    }
                }
                // Nothing that far out in this row (a notch in the outline): the nearest block in the row.
                for (int off = 0; off < w; off++) {
                    for (int c : new int[]{(int) axisBlocks + off, (int) axisBlocks - off}) {
                        if (c < 0 || c >= w) continue;
                        BlockState b = grid.block(c, v);
                        if (b != null) return b;
                    }
                }
                return null;
            }
        };
    }

    /**
     * A field of columns: at each half-block cell of the picture, filled from {@code lo} (0 when null) up to (not
     * including) {@code hi}, in halves out of the picture.
     */
    private static HalfField columns(int[] hi, int[] lo, int w, int h, int depth) {
        int w2 = 2 * w;
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
                return depth;
            }

            @Override
            public boolean filled(int su, int sv, int sd) {
                int i = sv * w2 + su;
                return sd < hi[i] && sd >= (lo == null ? 0 : lo[i]);
            }

            @Override
            public int columnDepth(int u, int v) {
                int t = 0;
                for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) t = Math.max(t, hi[(2 * v + b) * w2 + 2 * u + a]);
                return (t + 1) / 2;
            }
        };
    }
}
