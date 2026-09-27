package io.blockdesigner.pixelart.palette;

import io.blockdesigner.core.blocks.BlockFamily;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.pixelart.pipeline.OkLab;
import io.blockdesigner.pixelart.shape.SubBlock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Finds the slab or stair to use where a build needs half a block. The matched block's own slab or stair when its
 * family has one (stone → stone_slab); otherwise the closest-looking block that has that shape, from the chosen set
 * first and then from the building blocks (concrete, wool and terracotta come in no slabs or stairs).
 * Not thread safe (it caches); make one per build.
 */
public final class ShapeBlocks {

    /** A block's slab or stair id, if the game has one. */
    @FunctionalInterface
    public interface Families {
        Optional<String> member(BlockState full, BlockFamily.Shape shape);
    }

    private final Families families;
    private final List<BlockState> preferred, fallback;
    private final BlockColours.Source colours;
    private final boolean topFace;
    private final Map<String, Optional<String>> cache = new HashMap<>();

    /**
     * @param preferred the chosen block set: substitutes come from here first
     * @param fallback  where substitutes come from when the set has no block of that shape
     */
    public ShapeBlocks(Families families, List<BlockState> preferred, List<BlockState> fallback, BlockColours.Source colours, boolean topFace) {
        this.families = families;
        this.preferred = preferred;
        this.fallback = fallback;
        this.colours = colours;
        this.topFace = topFace;
    }

    /**
     * The block for a cell of material {@code full} with this shape; null for air, and also when no block anywhere
     * has the shape (then the caller falls back to a full block or air).
     */
    public BlockState block(BlockState full, SubBlock.Shape shape) {
        return switch (shape.kind()) {
            case AIR -> null;
            case FULL -> full;
            case SLAB, STAIRS -> {
                BlockFamily.Shape member = shape.kind() == SubBlock.Kind.SLAB ? BlockFamily.Shape.SLAB : BlockFamily.Shape.STAIRS;
                Optional<String> id = cache.computeIfAbsent(full + "|" + member, k -> find(full, member));
                if (id.isEmpty()) yield null;
                Map<String, String> props = new TreeMap<>(shape.properties());
                props.put("waterlogged", "false");
                yield BlockState.of(id.get(), props);
            }
        };
    }

    private Optional<String> find(BlockState full, BlockFamily.Shape member) {
        Optional<String> own = families.member(full, member);
        if (own.isPresent()) return own;
        float[] want = colours.look(full).face(topFace);
        for (List<BlockState> pool : List.of(preferred, fallback)) {
            String best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockState b : pool) {
                Optional<String> m = families.member(b, member);
                if (m.isEmpty()) continue;
                BlockColours.Look look = colours.look(b);
                if (look.seeThrough()) continue;
                double d = OkLab.dist2(want, look.face(topFace));
                if (d < bestD) {
                    bestD = d;
                    best = m.get();
                }
            }
            if (best != null) return Optional.of(best);
        }
        return Optional.empty();
    }
}
