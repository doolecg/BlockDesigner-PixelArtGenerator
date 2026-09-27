package io.blockdesigner.pixelart;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.pixelart.palette.BlockSets;
import io.blockdesigner.pixelart.pipeline.Picture;
import io.blockdesigner.pixelart.pipeline.Pipeline;
import io.blockdesigner.plugin.OptionValues;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineTest {

    private static Pipeline pipeline() {
        return new Pipeline(Fixtures.source(), Fixtures::exists, Fixtures::resolve);
    }

    private static OptionValues options() {
        return BuildOptions.OPTIONS.defaults().with("blocks", BlockSets.CONCRETE).with("width", 32);
    }

    @Test
    void buildsADiscWithoutItsBackground() {
        Picture disc = Fixtures.disc(64, Fixtures.RED, Fixtures.WHITE);
        Pipeline.Result r = pipeline().run(disc, BuildOptions.settings(options()));
        Box b = r.structure().bounds().orElseThrow();
        // The blended rim where the disc touches the picture's edge is dropped as halo.
        assertThat(b.maxX() - b.minX() + 1).isBetween(30, 32);
        assertThat(b.maxZ() - b.minZ() + 1).isEqualTo(1);
        // Roughly π/4 of the square, all red.
        assertThat(r.structure().blockCount()).isBetween(700L, 860L);
        assertThat(r.structure().usedStates()).containsExactly(BlockState.of("red_concrete"));
    }

    @Test
    void floorsLieFlat() {
        Picture p = Fixtures.picture(20, 10, (x, y) -> Fixtures.BLUE);
        Pipeline.Result r = pipeline().run(p, BuildOptions.settings(options().with("orientation", "Floor (top is north)").with("width", 20)));
        Box b = r.structure().bounds().orElseThrow();
        assertThat(b.maxY()).isEqualTo(b.minY());
        assertThat(r.structure().blockCount()).isEqualTo(200);
    }

    @Test
    void reusesEarlierStages() {
        Pipeline pl = pipeline();
        Picture p = Fixtures.disc(40, Fixtures.GREEN, Fixtures.WHITE);
        Pipeline.Result a = pl.run(p, BuildOptions.settings(options()));
        Pipeline.Result b = pl.run(p, BuildOptions.settings(options().with("orientation", "Wall facing east")));
        assertThat(b.segmentation()).isSameAs(a.segmentation());
        assertThat(b.grid()).isSameAs(a.grid());
        Pipeline.Result c = pl.run(p, BuildOptions.settings(options().with("method", "Regions (clean shapes)")));
        assertThat(c.segmentation()).isSameAs(a.segmentation());
        assertThat(c.grid()).isNotSameAs(a.grid());
    }

    @Test
    void customBlocks() {
        Picture p = Fixtures.picture(8, 8, (x, y) -> Fixtures.RED);
        Pipeline.Result r = pipeline().run(p, BuildOptions.settings(options().with("blocks", BlockSets.CUSTOM).with("custom", "white_concrete, black_concrete")));
        assertThat(r.grid().palette()).extracting(BlockState::name).containsAnyOf("minecraft:white_concrete", "minecraft:black_concrete");
        assertThatThrownBy(() -> pipeline().run(p, BuildOptions.settings(options().with("blocks", BlockSets.CUSTOM).with("custom", "nope_block"))))
                .hasMessageContaining("nope_block");
    }

    @Test
    void readsImageFiles() throws Exception {
        Path f = Files.createTempFile("pixel-art-generator", ".png");
        try {
            ImageIO.write(Fixtures.disc(16, Fixtures.RED, 0).toImage(), "png", f.toFile());
            Picture p = Picture.read(f);
            assertThat(p.width()).isEqualTo(16);
            assertThat(p.alpha(0, 0)).isZero();
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void everyBlockSetHasBlocks() {
        for (String set : BlockSets.NAMES)
            if (!set.equals(BlockSets.CUSTOM)) assertThat(BlockSets.ids(set)).as(set).isNotEmpty().allMatch(id -> id.startsWith("minecraft:"));
        assertThat(BlockSets.ids(BlockSets.COLOURS)).hasSize(49);
    }
}
