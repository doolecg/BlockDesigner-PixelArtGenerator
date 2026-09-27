package io.blockdesigner.pixelart.shape;

import io.blockdesigner.core.blocks.BlockFamily;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.pixelart.BuildOptions;
import io.blockdesigner.pixelart.Fixtures;
import io.blockdesigner.pixelart.palette.BlockColours;
import io.blockdesigner.pixelart.palette.BlockSets;
import io.blockdesigner.pixelart.palette.ShapeBlocks;
import io.blockdesigner.pixelart.pipeline.Picture;
import io.blockdesigner.pixelart.pipeline.Pipeline;
import io.blockdesigner.plugin.OptionValues;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReliefShapeTest {
    /** Every concrete comes with a slab and stairs in these tests. */
    private static final ShapeBlocks.Families ALL_SHAPES = (b, shape) ->
            Optional.of(b.name() + (shape == BlockFamily.Shape.SLAB ? "_slab" : "_stairs"));

    private static Pipeline pipeline() {
        return new Pipeline(Fixtures.source(), Fixtures::exists, Fixtures::resolve, ALL_SHAPES);
    }

    private static OptionValues relief(int width) {
        return BuildOptions.OPTIONS.defaults().with("mode", BuildMode.RELIEF.label).with("blocks", BlockSets.CONCRETE)
                .with("width", width).with("orientation", Orientation.FLOOR.label).with("background", "None")
                .with("smoothing", 0).with("base", 1).with("maxHeight", 8);
    }

    /** Dark on the left, light on the right: a ramp rising to the east. */
    private static Picture ramp(int w) {
        return Fixtures.picture(w, 8, (x, y) -> {
            int g = 30 + x * 200 / (w - 1);
            return 0xFF000000 | g << 16 | g << 8 | g;
        });
    }

    private static int top(Structure s, int x, int z) {
        for (int y = 100; y >= 0; y--) if (!s.get(x, y, z).isAir()) return y;
        return -1;
    }

    @Test
    void rampRisesTowardsTheLight() {
        Structure s = pipeline().run(ramp(32), BuildOptions.settings(relief(32))).structure();
        Box b = s.bounds().orElseThrow();
        assertThat(b.maxY() - b.minY() + 1).isBetween(7, 8);
        for (int x = 1; x < 32; x++) assertThat(top(s, x, 4)).isGreaterThanOrEqualTo(top(s, x - 1, 4));
    }

    @Test
    void slopesGetStairsFacingUphill() {
        Structure s = pipeline().run(ramp(32), BuildOptions.settings(relief(32))).structure();
        Set<String> facings = new HashSet<>();
        boolean slabs = false;
        for (BlockState st : s.usedStates()) {
            if (st.name().endsWith("_stairs")) facings.add(st.get("facing"));
            if (st.name().endsWith("_slab")) slabs = true;
        }
        // Rising to the east: stairs have their tall back to the east.
        assertThat(facings).as(s.usedStates().toString()).containsOnly("east");
        assertThat(slabs).isTrue();
    }

    @Test
    void domeStairsAllFaceUphill() {
        Picture dome = Fixtures.picture(40, 40, (x, y) -> {
            double d = Math.hypot(x - 19.5, y - 19.5) / 20;
            int g = (int) Math.clamp(255 * (1 - d * d), 0, 255);
            return 0xFF000000 | g << 16 | g << 8 | g;
        });
        Structure s = pipeline().run(dome, BuildOptions.settings(relief(40).with("maxHeight", 10))).structure();
        Box b = s.bounds().orElseThrow();
        double cx = (b.minX() + b.maxX()) / 2.0, cz = (b.minZ() + b.maxZ()) / 2.0;
        int[] stairs = {0}, downhill = {0};
        s.forEachBlock((x, y, z, st) -> {
            if (!st.name().endsWith("_stairs") || !st.get("half").equals("bottom")) return;
            stairs[0]++;
            // The tall side faces the centre: its direction and the way to the centre never point apart.
            double dx = cx - x, dz = cz - z;
            double dot = switch (st.get("facing")) {
                case "north" -> -dz;
                case "south" -> dz;
                case "east" -> dx;
                default -> -dx;
            };
            if (dot < -1) downhill[0]++;
        });
        assertThat(stairs[0]).isGreaterThan(50);
        assertThat(downhill[0]).isZero();
    }

    @Test
    void wholeBlocksWhenSlabsAndStairsAreOff() {
        Structure s = pipeline().run(ramp(32), BuildOptions.settings(relief(32).with("slabs", false).with("stairs", false))).structure();
        assertThat(s.usedStates()).allMatch(st -> st.name().endsWith("_concrete"));
    }

    @Test
    void terracesMakeLevels() {
        Structure s = pipeline().run(ramp(32), BuildOptions.settings(relief(32).with("terraces", 3).with("slabs", false).with("stairs", false))).structure();
        Set<Integer> tops = new HashSet<>();
        for (int x = 0; x < 32; x++) tops.add(top(s, x, 4));
        assertThat(tops).hasSize(3);
    }

    @Test
    void hollowKeepsAShell() {
        OptionValues v = relief(32).with("slabs", false).with("stairs", false);
        long solid = pipeline().run(ramp(32), BuildOptions.settings(v)).structure().blockCount();
        Structure hollow = pipeline().run(ramp(32), BuildOptions.settings(v.with("hollow", true))).structure();
        assertThat(hollow.blockCount()).isLessThan(solid);
        // Every column still has its top block.
        for (int x = 0; x < 32; x++) assertThat(top(hollow, x, 4)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void wallReliefComesOutTowardsTheViewer() {
        Structure s = pipeline().run(ramp(32), BuildOptions.settings(relief(32).with("orientation", Orientation.SOUTH.label)
                .with("slabs", false).with("stairs", false))).structure();
        Box b = s.bounds().orElseThrow();
        assertThat(b.sizeZ()).isBetween(7, 8);
        assertThat(b.sizeY()).isEqualTo(8);
    }

    @Test
    void invertSwapsHighAndLow() {
        Structure s = pipeline().run(ramp(32), BuildOptions.settings(relief(32).with("invert", true))).structure();
        assertThat(top(s, 0, 4)).isGreaterThan(top(s, 31, 4));
    }

    @Test
    void layersFromColourRegions() {
        // Three flat bands of colour: three flat levels.
        Picture bands = Fixtures.picture(30, 6, (x, y) -> x < 10 ? Fixtures.BLACK : x < 20 ? 0xFF808080 : Fixtures.WHITE);
        Structure s = pipeline().run(bands, BuildOptions.settings(relief(30).with("heightFrom", ReliefShape.Source.REGIONS.label)
                .with("slabs", false).with("stairs", false))).structure();
        assertThat(top(s, 2, 3)).isLessThan(top(s, 15, 3));
        assertThat(top(s, 15, 3)).isLessThan(top(s, 27, 3));
        assertThat(top(s, 12, 3)).isEqualTo(top(s, 17, 3));
    }

    @Test
    void shapesComeFromAnotherBlockWhenTheMaterialHasNone() {
        // Only stone has stairs: red concrete's stairs are the closest block that has them.
        ShapeBlocks.Families onlyStone = (b, shape) -> b.name().equals("minecraft:stone") ? Optional.of("minecraft:stone_stairs") : Optional.empty();
        BlockColours.Source src = Fixtures.source();
        ShapeBlocks shapes = new ShapeBlocks(onlyStone, List.of(BlockState.of("red_concrete")), List.of(BlockState.of("stone")), src, true);
        SubBlock.Shape stair = SubBlock.allStairs().getFirst();
        assertThat(shapes.block(BlockState.of("red_concrete"), stair).name()).isEqualTo("minecraft:stone_stairs");
        ShapeBlocks none = new ShapeBlocks((b, shape) -> Optional.empty(), List.of(), List.of(), src, true);
        assertThat(none.block(BlockState.of("red_concrete"), stair)).isNull();
    }
}
