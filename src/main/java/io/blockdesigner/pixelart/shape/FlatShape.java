package io.blockdesigner.pixelart.shape;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.pixelart.palette.BlockGrid;

/** Pixel art: the block grid as a wall or floor one block thick. */
public final class FlatShape {

    private FlatShape() {
    }

    public static Structure build(BlockGrid grid, Orientation orientation) {
        Structure s = new Structure();
        for (int v = 0; v < grid.height(); v++)
            for (int u = 0; u < grid.width(); u++) {
                BlockState b = grid.block(u, v);
                if (b != null) s.set(orientation.place(u, v, 0, grid.width(), grid.height(), 1), b);
            }
        return s;
    }
}
