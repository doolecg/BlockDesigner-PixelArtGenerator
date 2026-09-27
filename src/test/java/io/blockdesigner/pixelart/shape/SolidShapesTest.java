package io.blockdesigner.pixelart.shape;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.pixelart.BuildOptions;
import io.blockdesigner.pixelart.Fixtures;
import io.blockdesigner.pixelart.palette.BlockSets;
import io.blockdesigner.pixelart.pipeline.Picture;
import io.blockdesigner.pixelart.pipeline.Pipeline;
import io.blockdesigner.plugin.OptionValues;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SolidShapesTest {

    private static Pipeline pipeline() {
        return new Pipeline(Fixtures.source(), Fixtures::exists, Fixtures::resolve);
    }

    private static OptionValues solid(BuildMode mode, int width) {
        return BuildOptions.OPTIONS.defaults().with("mode", mode.label).with("blocks", BlockSets.CONCRETE).with("width", width)
                .with("slabs", false).with("stairs", false);
    }

    private static int filled(HalfField f) {
        int n = 0;
        for (int su = 0; su < 2 * f.width(); su++)
            for (int sv = 0; sv < 2 * f.height(); sv++)
                for (int sd = 0; sd < 2 * f.depth(); sd++) if (f.filled(su, sv, sd)) n++;
        return n;
    }

    @Test
    void distanceTransformIsExact() {
        SplittableRandom r = new SplittableRandom(3);
        int w = 23, h = 17;
        boolean[] in = new boolean[w * h];
        for (int i = 0; i < in.length; i++) in[i] = r.nextInt(5) != 0;
        double[] d = DistanceTransform.of(in, w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double best = 0;
                if (in[y * w + x]) {
                    best = Double.MAX_VALUE;
                    // Outside: every out cell, and the ring just beyond the grid.
                    for (int yy = -1; yy <= h; yy++)
                        for (int xx = -1; xx <= w; xx++) {
                            boolean out = xx < 0 || yy < 0 || xx >= w || yy >= h || !in[yy * w + xx];
                            if (out) best = Math.min(best, Math.hypot(xx - x, yy - y));
                        }
                }
                assertThat(d[y * w + x]).as("%d,%d", x, y).isCloseTo(best, within(1e-9));
            }
    }

    @Test
    void extrudeIsTheOutlineTimesTheThickness() {
        boolean[] square = new boolean[100];
        java.util.Arrays.fill(square, true);
        HalfField f = SolidShapes.extrude(square, 10, 10, 3, 0);
        assertThat(filled(f)).isEqualTo(20 * 20 * 6);
        HalfField bevelled = SolidShapes.extrude(square, 10, 10, 3, 2);
        assertThat(filled(bevelled)).isLessThan(20 * 20 * 6).isGreaterThan(20 * 20 * 3);
        // The bevel only takes from the front: the back face is whole.
        for (int su = 0; su < 20; su++) assertThat(bevelled.filled(su, 0, 0)).isTrue();
        assertThat(bevelled.filled(0, 0, 5)).isFalse();
        assertThat(bevelled.filled(10, 10, 5)).isTrue();
    }

    @Test
    void inflatingADiscMakesABall() {
        Picture disc = Fixtures.disc(40, Fixtures.RED, Fixtures.WHITE);
        Structure s = pipeline().run(disc, BuildOptions.settings(solid(BuildMode.INFLATE, 40))).structure();
        Box b = s.bounds().orElseThrow();
        // Standing up (a wall facing south): as deep as it is wide.
        assertThat(b.sizeZ()).isCloseTo(b.sizeX(), within(3));
        // Roughly a ball's volume: 4/3·π·r³.
        double r = b.sizeX() / 2.0;
        assertThat((double) s.blockCount()).isCloseTo(4.0 / 3 * Math.PI * r * r * r, within(0.2 * 4.0 / 3 * Math.PI * r * r * r));
        // Front and back mirror each other.
        int mid = (b.minY() + b.maxY()) / 2, cx = (b.minX() + b.maxX()) / 2;
        int front = 0, back = 0;
        for (int z = b.minZ(); z <= b.maxZ(); z++) if (!s.get(cx, mid, z).isAir()) {
            if (z < (b.minZ() + b.maxZ()) / 2.0) back++;
            else front++;
        }
        assertThat(Math.abs(front - back)).isLessThanOrEqualTo(1);
    }

    @Test
    void puffinessMakesItFlatterOrRounder() {
        Picture disc = Fixtures.disc(40, Fixtures.RED, Fixtures.WHITE);
        int flat = pipeline().run(disc, BuildOptions.settings(solid(BuildMode.INFLATE, 40).with("puffiness", 0.4))).structure().bounds().orElseThrow().sizeZ();
        int round = pipeline().run(disc, BuildOptions.settings(solid(BuildMode.INFLATE, 40))).structure().bounds().orElseThrow().sizeZ();
        assertThat(flat).isLessThan(round / 2 + 2);
    }

    @Test
    void revolvingARectangleMakesACylinder() {
        // A 20-wide, 10-tall bar on white: a cylinder of radius 10, 10 high.
        Picture bar = Fixtures.picture(40, 20, (x, y) -> x >= 10 && x < 30 && y >= 5 && y < 15 ? Fixtures.BLUE : Fixtures.WHITE);
        Structure s = pipeline().run(bar, BuildOptions.settings(solid(BuildMode.REVOLVE, 40))).structure();
        Box b = s.bounds().orElseThrow();
        assertThat(b.sizeY()).isEqualTo(10);
        assertThat(b.sizeX()).isEqualTo(20);
        assertThat(b.sizeZ()).isEqualTo(20);
        assertThat((double) s.blockCount()).isCloseTo(Math.PI * 100 * 10, within(0.1 * Math.PI * 100 * 10));
        assertThat(s.usedStates()).containsExactly(BlockState.of("blue_concrete"));
    }

    @Test
    void revolveWrapsColoursRoundTheAxis() {
        // Red core, blue rim: the rim stays blue all the way round, the core red.
        Picture vase = Fixtures.picture(40, 14, (x, y) -> x < 10 || x >= 30 || y < 2 || y >= 12 ? Fixtures.WHITE : x < 14 || x >= 26 ? Fixtures.BLUE : Fixtures.RED);
        Structure s = pipeline().run(vase, BuildOptions.settings(solid(BuildMode.REVOLVE, 40))).structure();
        Box b = s.bounds().orElseThrow();
        int cx = (b.minX() + b.maxX()) / 2, cz = (b.minZ() + b.maxZ()) / 2;
        // Front-most and back-most blocks through the middle are rim.
        assertThat(s.get(cx, 5, b.minZ()).name()).isEqualTo("minecraft:blue_concrete");
        assertThat(s.get(cx, 5, b.maxZ()).name()).isEqualTo("minecraft:blue_concrete");
        assertThat(s.get(cx, 5, cz).name()).isEqualTo("minecraft:red_concrete");
    }

    @Test
    void hollowLeavesTheInsideEmpty() {
        Picture disc = Fixtures.disc(30, Fixtures.RED, Fixtures.WHITE);
        OptionValues v = solid(BuildMode.INFLATE, 30);
        Structure full = pipeline().run(disc, BuildOptions.settings(v)).structure();
        Structure shell = pipeline().run(disc, BuildOptions.settings(v.with("hollow", true))).structure();
        assertThat(shell.blockCount()).isLessThan(full.blockCount() / 2);
        Box b = shell.bounds().orElseThrow();
        assertThat(shell.get((b.minX() + b.maxX()) / 2, (b.minY() + b.maxY()) / 2, (b.minZ() + b.maxZ()) / 2).isAir()).isTrue();
    }

    @Test
    void threeDModesAreCappedSmaller() {
        OptionValues v = solid(BuildMode.INFLATE, 500);
        assertThat(BuildOptions.settings(v).prepare().width()).isEqualTo(256);
        assertThat(BuildOptions.settings(v.with("mode", BuildMode.FLAT.label)).prepare().width()).isEqualTo(500);
    }
}
