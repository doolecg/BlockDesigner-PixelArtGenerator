package io.blockdesigner.pixelart.shape;

import io.blockdesigner.core.model.BlockPos;

/**
 * Which way the picture faces in the world. For a wall, the picture reads the right way round for someone standing
 * on that side of it; for a floor, the top of the picture is north.
 */
public enum Orientation {
    SOUTH("Wall facing south"),
    NORTH("Wall facing north"),
    EAST("Wall facing east"),
    WEST("Wall facing west"),
    FLOOR("Floor (top is north)");

    public final String label;

    Orientation(String label) {
        this.label = label;
    }

    public static Orientation of(String label) {
        for (Orientation o : values()) if (o.label.equals(label)) return o;
        return SOUTH;
    }

    public boolean floor() {
        return this == FLOOR;
    }

    /**
     * An octant of a block in picture terms turned into world terms (a bit for {@link SubBlock}).
     *
     * @param a across, 0 left half, 1 right half
     * @param b 0 lower half of the picture's up direction, 1 upper
     * @param c 0 back half, 1 the half towards the viewer
     */
    public int octantBit(int a, int b, int c) {
        BlockPos p = place(a, 1 - b, c, 2, 2, 2);
        return SubBlock.bit(p.x(), p.y(), p.z());
    }

    /**
     * Where a point of the picture goes.
     *
     * @param u     across the picture, 0 at its left
     * @param v     down the picture, 0 at its top
     * @param depth out of the picture towards the viewer, 0 at the picture's back
     * @param w     the picture's width
     * @param h     the picture's height
     * @param d     the build's depth (for walls facing north or west, which count depth the other way)
     */
    public BlockPos place(int u, int v, int depth, int w, int h, int d) {
        int up = h - 1 - v;
        return switch (this) {
            // Seen from the south (+z), looking north: right is +x.
            case SOUTH -> new BlockPos(u, up, depth);
            // Seen from the north, looking south: right is −x.
            case NORTH -> new BlockPos(w - 1 - u, up, d - 1 - depth);
            // Seen from the east, looking west: right is −z.
            case EAST -> new BlockPos(depth, up, w - 1 - u);
            // Seen from the west, looking east: right is +z.
            case WEST -> new BlockPos(d - 1 - depth, up, u);
            // Looking down with north (−z) at the top of the picture.
            case FLOOR -> new BlockPos(u, depth, v);
        };
    }
}
