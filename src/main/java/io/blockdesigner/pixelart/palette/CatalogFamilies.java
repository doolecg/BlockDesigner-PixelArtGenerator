package io.blockdesigner.pixelart.palette;

import io.blockdesigner.core.blocks.BlockFamily;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.plugin.BlockCatalog;

import java.util.Optional;

/** Slabs and stairs from BlockDesigner's block families, limited to blocks the loaded game has. */
public final class CatalogFamilies {
    private CatalogFamilies() {
    }

    public static ShapeBlocks.Families of(BlockCatalog catalog) {
        return (BlockState full, BlockFamily.Shape shape) -> catalog.family(full).flatMap(f -> f.get(shape)).filter(catalog::exists);
    }

    /** Kept for symmetry with {@link #of}: no slabs or stairs at all. */
    public static ShapeBlocks.Families none() {
        return (full, shape) -> Optional.empty();
    }
}
