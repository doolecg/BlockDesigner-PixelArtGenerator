package io.blockdesigner.pixelart.palette;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.pixelart.Fixtures;
import io.blockdesigner.pixelart.pipeline.Background;
import io.blockdesigner.pixelart.pipeline.LabImage;
import io.blockdesigner.pixelart.pipeline.Picture;
import io.blockdesigner.pixelart.pipeline.Prepare;
import io.blockdesigner.pixelart.pipeline.Segmentation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockGridTest {
    private static final List<BlockState> CONCRETE = Fixtures.COLOURS.keySet().stream().sorted().map(BlockState::of).toList();

    private static BlockGrid grid(Picture p, BlockGrid.Method method, int maxBlocks) {
        LabImage img = Prepare.run(p, new Prepare.Settings(p.width(), 512, false, 0, 1, 1));
        Segmentation seg = Segmentation.run(img, Background.mask(img, Background.Mode.NONE, 0), 8, 1, 1);
        return BlockGrid.match(img, seg, new Matcher(CONCRETE, Fixtures.source(), false, 0.5), method, maxBlocks);
    }

    @Test
    void picksTheClosestBlock() {
        Matcher m = new Matcher(CONCRETE, Fixtures.source(), false, 0);
        float[] darkRed = io.blockdesigner.pixelart.pipeline.OkLab.fromArgb(0xFFB01818);
        assertThat(m.get(m.nearest(darkRed)).block().name()).isEqualTo("minecraft:red_concrete");
    }

    @Test
    void busyTexturesLoseCloseCalls() {
        // Stone (noisy in the fake source) and gray concrete (flat) are both mid grey.
        List<BlockState> greys = List.of(BlockState.of("stone"), BlockState.of("gray_concrete"));
        float[] grey = io.blockdesigner.pixelart.pipeline.OkLab.fromArgb(0xFF808080);
        assertThat(new Matcher(greys, Fixtures.source(), false, 1).get(new Matcher(greys, Fixtures.source(), false, 1).nearest(grey)).block().name())
                .isEqualTo("minecraft:gray_concrete");
    }

    @Test
    void everyMethodBuildsTheSubject() {
        Picture p = Fixtures.picture(16, 8, (x, y) -> x < 8 ? Fixtures.RED : Fixtures.BLUE);
        for (BlockGrid.Method m : BlockGrid.Method.values()) {
            BlockGrid g = grid(p, m, 0);
            assertThat(g.block(0, 0).name()).as(m.name()).isEqualTo("minecraft:red_concrete");
            assertThat(g.block(15, 7).name()).as(m.name()).isEqualTo("minecraft:blue_concrete");
        }
    }

    @Test
    void ditheringMixesBlocksForInBetweenColours() {
        // Purple between red and blue: plain matching picks one block, dithering mixes both.
        Picture p = Fixtures.picture(16, 16, (x, y) -> 0xFF8030A0);
        List<BlockState> redBlue = List.of(BlockState.of("red_concrete"), BlockState.of("blue_concrete"));
        LabImage img = Prepare.run(p, new Prepare.Settings(16, 512, false, 0, 1, 1));
        Segmentation seg = Segmentation.run(img, Background.mask(img, Background.Mode.NONE, 0), 4, 1, 1);
        Matcher m = new Matcher(redBlue, Fixtures.source(), false, 0);
        assertThat(BlockGrid.match(img, seg, m, BlockGrid.Method.PIXELS, 0).palette()).hasSize(1);
        assertThat(BlockGrid.match(img, seg, m, BlockGrid.Method.DITHER_DIFFUSE, 0).palette()).hasSize(2);
        assertThat(BlockGrid.match(img, seg, m, BlockGrid.Method.DITHER_ORDERED, 0).palette()).hasSize(2);
    }

    @Test
    void limitsTheKindsOfBlock() {
        Picture p = Fixtures.picture(30, 10, (x, y) -> x < 12 ? Fixtures.RED : x < 22 ? Fixtures.BLUE : x < 29 ? Fixtures.GREEN : Fixtures.WHITE);
        assertThat(grid(p, BlockGrid.Method.PIXELS, 0).palette()).hasSize(4);
        BlockGrid two = grid(p, BlockGrid.Method.PIXELS, 2);
        assertThat(two.palette()).extracting(BlockState::name).containsExactly("minecraft:red_concrete", "minecraft:blue_concrete");
    }

    @Test
    void paletteIsMostUsedFirst() {
        BlockGrid g = grid(Fixtures.picture(10, 10, (x, y) -> x < 3 ? Fixtures.RED : Fixtures.BLUE), BlockGrid.Method.REGIONS, 0);
        assertThat(g.palette().getFirst().name()).isEqualTo("minecraft:blue_concrete");
        assertThat(g.counts()).containsExactly(70, 30);
    }

    @Test
    void seeThroughBlocksAreLeftOut() {
        BlockColours.Source glassy = b -> new BlockColours.Look(new float[3], new float[3], 0, true);
        assertThatThrownBy(() -> new Matcher(List.of(BlockState.of("glass")), glassy, false, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
