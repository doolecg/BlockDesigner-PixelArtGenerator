package io.blockdesigner.pixelart.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Half-block detail with the blocks Minecraft has: a block split into 8 octants (2×2×2), each filled or not, becomes
 * the closest of a full block, air, a slab (top or bottom) or a stair (any facing, half and corner shape).
 *
 * <p>An octant is a bit: {@code x + 2·z + 4·y}, each 0 or 1 within the block, in world axes (x east, y up, z south).
 * Patterns no block has exactly (a vertical half, say) take the closest shape by how many octants differ.
 */
public final class SubBlock {

    /** What a block cell becomes. */
    public enum Kind { AIR, FULL, SLAB, STAIRS }

    /**
     * One shape.
     *
     * @param kind       what it is
     * @param mask       the octants it fills
     * @param properties block state properties for it (facing, half, shape for stairs; type for slabs), empty for full and air
     */
    public record Shape(Kind kind, int mask, Map<String, String> properties) {
    }

    public static final Shape AIR = new Shape(Kind.AIR, 0, Map.of());
    public static final Shape FULL = new Shape(Kind.FULL, 255, Map.of());

    private static final List<Shape> SLABS = List.of(
            new Shape(Kind.SLAB, 0x0F, Map.of("type", "bottom")),
            new Shape(Kind.SLAB, 0xF0, Map.of("type", "top")));
    private static final List<Shape> STAIRS = stairs();

    /** Best shape per pattern for each combination of allowed kinds (index: slabs? 1 : 0 | stairs? 2 : 0). */
    private static final Shape[][] TABLES = new Shape[4][];

    static {
        for (int allow = 0; allow < 4; allow++) {
            List<Shape> candidates = new ArrayList<>(List.of(AIR, FULL));
            if ((allow & 1) != 0) candidates.addAll(SLABS);
            if ((allow & 2) != 0) candidates.addAll(STAIRS);
            Shape[] table = new Shape[256];
            for (int m = 0; m < 256; m++) table[m] = closest(m, candidates);
            TABLES[allow] = table;
        }
    }

    private SubBlock() {
    }

    /** The shape for a pattern of filled octants. */
    public static Shape of(int mask, boolean slabs, boolean stairs) {
        return TABLES[(slabs ? 1 : 0) | (stairs ? 2 : 0)][mask & 0xFF];
    }

    public static int bit(int x, int y, int z) {
        return 1 << (x + 2 * z + 4 * y);
    }

    /** Every stair: 4 facings × 2 halves × 5 shapes. */
    public static List<Shape> allStairs() {
        return STAIRS;
    }

    /**
     * Fewest differing octants wins. On a tie, fewer octants filled than wanted beats more (so thin details don't
     * swell), then the simpler shape (the candidates' order: air, full, slabs, stairs).
     */
    private static Shape closest(int mask, List<Shape> candidates) {
        Shape best = null;
        int bestDiff = Integer.MAX_VALUE, bestExtra = Integer.MAX_VALUE;
        for (Shape s : candidates) {
            int diff = Integer.bitCount(s.mask() ^ mask);
            int extra = Integer.bitCount(s.mask() & ~mask);
            if (diff < bestDiff || (diff == bestDiff && extra < bestExtra)) {
                best = s;
                bestDiff = diff;
                bestExtra = extra;
            }
        }
        return best;
    }

    /**
     * Vanilla stair geometry. The full-height back of a stair is on its {@code facing} side; "left" and "right" are
     * as seen looking towards {@code facing} (facing north, left is west). A top-half stair is the bottom one upside
     * down.
     */
    private static List<Shape> stairs() {
        String[] facings = {"north", "east", "south", "west"};
        int[][] forward = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        List<Shape> out = new ArrayList<>();
        for (int f = 0; f < 4; f++) {
            int fx = forward[f][0], fz = forward[f][1];
            // Left of forward: (fz, −fx) — north's left is west, east's left is north.
            int lx = fz, lz = -fx;
            for (String half : new String[]{"bottom", "top"}) {
                int base = half.equals("bottom") ? 0 : 1, step = 1 - base;
                for (String shape : new String[]{"straight", "inner_left", "inner_right", "outer_left", "outer_right"}) {
                    int mask = 0;
                    for (int x = 0; x < 2; x++)
                        for (int z = 0; z < 2; z++) {
                            mask |= bit(x, base, z);
                            int dx = 2 * x - 1, dz = 2 * z - 1;
                            boolean front = dx * fx + dz * fz > 0, left = dx * lx + dz * lz > 0;
                            boolean upper = switch (shape) {
                                case "straight" -> front;
                                case "outer_left" -> front && left;
                                case "outer_right" -> front && !left;
                                case "inner_left" -> front || left;
                                default -> front || !left; // inner_right
                            };
                            if (upper) mask |= bit(x, step, z);
                        }
                    Map<String, String> props = new TreeMap<>(Map.of("facing", facings[f], "half", half, "shape", shape));
                    out.add(new Shape(Kind.STAIRS, mask, Map.copyOf(props)));
                }
            }
        }
        return List.copyOf(out);
    }
}
