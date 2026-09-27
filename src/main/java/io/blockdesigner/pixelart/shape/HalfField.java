package io.blockdesigner.pixelart.shape;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.pixelart.palette.BlockGrid;
import io.blockdesigner.pixelart.palette.ShapeBlocks;

/**
 * A solid at half-block resolution in picture terms, before it is turned into blocks: {@code 2w × 2h × 2d} cells,
 * across the picture (left to right), down it (top to bottom) and out of it (back to front, towards the viewer).
 * Relief, extrusion and the 3D guesses all make one of these; {@link #build} turns it into full blocks, slabs and
 * stairs.
 */
public interface HalfField {

    /** The picture's width in blocks. */
    int width();

    /** The picture's height in blocks. */
    int height();

    /** How deep the solid is, in blocks. */
    int depth();

    /**
     * Whether a half-block cell is solid.
     *
     * @param su across, 0 … 2·width − 1
     * @param sv down, 0 … 2·height − 1
     * @param sd out, 0 … 2·depth − 1
     */
    boolean filled(int su, int sv, int sd);

    /** How many blocks deep the column under picture pixel (u, v) can have anything (a speed-up; depth() when unsure). */
    default int columnDepth(int u, int v) {
        return depth();
    }

    /**
     * The block for the cell at picture pixel (u, v), k blocks out: the pixel's own block by default (the picture
     * seen from the front). A lathe overrides it to wrap the picture's colours round.
     */
    default BlockState material(BlockGrid grid, int u, int v, int k) {
        return grid.block(u, v);
    }

    /**
     * Only the outside of a solid: cells more than {@code halves} half blocks from its surface are left empty (a
     * shell one block thick for 2). Cells at the field's own boundary count as surface.
     */
    static HalfField hollow(HalfField f, int halves) {
        int w2 = 2 * f.width(), h2 = 2 * f.height(), d2 = 2 * f.depth();
        return new HalfField() {
            @Override
            public int width() {
                return f.width();
            }

            @Override
            public int height() {
                return f.height();
            }

            @Override
            public int depth() {
                return f.depth();
            }

            @Override
            public int columnDepth(int u, int v) {
                return f.columnDepth(u, v);
            }

            @Override
            public BlockState material(BlockGrid grid, int u, int v, int k) {
                return f.material(grid, u, v, k);
            }

            @Override
            public boolean filled(int su, int sv, int sd) {
                if (!f.filled(su, sv, sd)) return false;
                for (int t = 1; t <= halves; t++)
                    if (!in(su - t, sv, sd) || !in(su + t, sv, sd) || !in(su, sv - t, sd) || !in(su, sv + t, sd)
                            || !in(su, sv, sd - t) || !in(su, sv, sd + t)) return true;
                return false;
            }

            private boolean in(int su, int sv, int sd) {
                return su >= 0 && sv >= 0 && sd >= 0 && su < w2 && sv < h2 && sd < d2 && f.filled(su, sv, sd);
            }
        };
    }

    /**
     * Turns the field into blocks: each block cell's 8 octants become a full block, slab, stair or air, in the
     * material the block grid has for that pixel.
     */
    static Structure build(HalfField f, BlockGrid grid, Orientation o, ShapeBlocks shapes, boolean slabs, boolean stairs) {
        Structure s = new Structure();
        int w = f.width(), h = f.height(), d = f.depth();
        // Picture octant (a across, b up, c out) → world bit, once.
        int[] bits = new int[8];
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) for (int c = 0; c < 2; c++) bits[a + 2 * b + 4 * c] = o.octantBit(a, b, c);
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++) {
                int cd = Math.min(d, f.columnDepth(u, v));
                for (int k = 0; k < cd; k++) {
                    int mask = 0;
                    for (int a = 0; a < 2; a++)
                        for (int b = 0; b < 2; b++)
                            for (int c = 0; c < 2; c++)
                                if (f.filled(2 * u + a, 2 * v + 1 - b, 2 * k + c)) mask |= bits[a + 2 * b + 4 * c];
                    if (mask == 0) continue;
                    BlockState material = f.material(grid, u, v, k);
                    if (material == null) continue;
                    SubBlock.Shape shape = SubBlock.of(mask, slabs, stairs);
                    BlockState block = shapes.block(material, shape);
                    // No slab or stair to be had in any block: whole blocks only for this cell.
                    if (block == null && shape.kind() != SubBlock.Kind.AIR)
                        block = shapes.block(material, SubBlock.of(mask, false, false));
                    if (block != null) s.set(o.place(u, v, k, w, h, d), block);
                }
            }
        return s;
    }
}
