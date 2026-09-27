package io.blockdesigner.pixelart.shape;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;

import java.util.List;

/** Turns a build about the vertical axis in quarter turns, clockwise seen from above, facing properties included. */
public final class Rotate {
    private static final List<String> FACINGS = List.of("north", "east", "south", "west");

    private Rotate() {
    }

    /** A copy turned {@code quarters} quarter turns clockwise, with its lowest corner at the origin. */
    public static Structure quarters(Structure s, int quarters) {
        int q = Math.floorMod(quarters, 4);
        Structure out = new Structure();
        s.forEachBlock((x, y, z, st) -> {
            int nx = x, nz = z;
            for (int i = 0; i < q; i++) {
                int t = nx;
                nx = -nz;
                nz = t;
            }
            out.set(nx, y, nz, state(st, q));
        });
        out.normalizeToOrigin();
        return out;
    }

    /** The block turned: its facing moves round by the same quarter turns, and x and z axes swap on odd turns. */
    public static BlockState state(BlockState st, int quarters) {
        String axis = st.get("axis");
        if (axis != null && Math.floorMod(quarters, 2) == 1 && !axis.equals("y"))
            return st.with("axis", axis.equals("x") ? "z" : "x");
        String f = st.get("facing");
        if (f == null) return st;
        int i = FACINGS.indexOf(f);
        return i < 0 ? st : st.with("facing", FACINGS.get(Math.floorMod(i + quarters, 4)));
    }

    /** Where a block of a turned build goes so the build stands centred on {@code at}, its bottom level with it. */
    public static BlockPos offsetCentredOn(Structure s, BlockPos at) {
        var b = s.bounds().orElseThrow();
        return new BlockPos(at.x() - b.minX() - b.sizeX() / 2, at.y() - b.minY(), at.z() - b.minZ() - b.sizeZ() / 2);
    }
}
