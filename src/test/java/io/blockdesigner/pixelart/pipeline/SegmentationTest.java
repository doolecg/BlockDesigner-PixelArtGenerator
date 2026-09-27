package io.blockdesigner.pixelart.pipeline;

import io.blockdesigner.pixelart.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentationTest {

    private static LabImage lab(Picture p) {
        return Prepare.run(p, new Prepare.Settings(p.width(), 512, false, 0, 1, 1));
    }

    private static boolean[] all(int n) {
        boolean[] m = new boolean[n];
        Arrays.fill(m, true);
        return m;
    }

    @Test
    void kmeansFindsTheColours() {
        LabImage img = lab(Fixtures.picture(20, 20, (x, y) -> x < 10 ? Fixtures.RED : y < 10 ? Fixtures.BLUE : Fixtures.GREEN));
        KMeans.Result r = KMeans.run(img, null, 8, 1);
        assertThat(r.k()).isEqualTo(3); // no more clusters than distinct colours
        assertThat(r.assignment()[0]).isNotEqualTo(r.assignment()[15]);
    }

    @Test
    void kmeansIsDeterministic() {
        LabImage img = lab(Fixtures.picture(40, 40, (x, y) -> 0xFF000000 | (x * 6) << 16 | (y * 6) << 8 | 90));
        assertThat(KMeans.run(img, null, 6, 7).assignment()).isEqualTo(KMeans.run(img, null, 6, 7).assignment());
    }

    @Test
    void splitsSameColourPatchesThatDontTouch() {
        // Two red squares apart on blue: two colours, three regions.
        LabImage img = lab(Fixtures.picture(20, 10, (x, y) -> (x < 5 || x >= 15) && y < 5 ? Fixtures.RED : Fixtures.BLUE));
        Segmentation s = Segmentation.run(img, all(img.size()), 2, 1, 1);
        assertThat(s.regions()).hasSize(3);
    }

    @Test
    void mergesSpeckleIntoItsNeighbour() {
        LabImage img = lab(Fixtures.picture(20, 20, (x, y) -> x == 7 && y == 7 ? Fixtures.GREEN : x < 10 ? Fixtures.RED : Fixtures.BLUE));
        Segmentation keep = Segmentation.run(img, all(img.size()), 3, 1, 1);
        Segmentation merged = Segmentation.run(img, all(img.size()), 3, 4, 1);
        assertThat(keep.regions()).hasSize(3);
        assertThat(merged.regions()).hasSize(2);
        assertThat(merged.region()[7 * 20 + 7]).isEqualTo(merged.region()[0]);
    }

    @Test
    void backgroundFromTheBorder() {
        LabImage img = lab(Fixtures.disc(32, Fixtures.RED, Fixtures.WHITE));
        boolean[] m = Background.mask(img, Background.Mode.AUTO, 0.08);
        assertThat(m[0]).isFalse();
        assertThat(m[16 * 32 + 16]).isTrue();
    }

    @Test
    void backgroundFloodStopsAtTheSubject() {
        // A white hole inside a red ring stays: it isn't connected to the border.
        LabImage img = lab(Fixtures.picture(30, 30, (x, y) -> {
            double d = Math.hypot(x - 14.5, y - 14.5);
            return d < 12 && d > 5 ? Fixtures.RED : Fixtures.WHITE;
        }));
        boolean[] m = Background.mask(img, Background.Mode.BORDER, 0.08);
        assertThat(m[0]).isFalse();
        assertThat(m[15 * 30 + 15]).isTrue();
    }

    @Test
    void backgroundFromTransparency() {
        LabImage img = lab(Fixtures.picture(10, 10, (x, y) -> x < 5 ? Fixtures.RED : 0));
        boolean[] m = Background.mask(img, Background.Mode.AUTO, 0.08);
        assertThat(m[0]).isTrue();
        assertThat(m[9]).isFalse();
    }

    @Test
    void noBackgroundForBusyBorders() {
        LabImage img = lab(Fixtures.picture(16, 16, (x, y) -> (x + y) % 3 == 0 ? Fixtures.RED : (x + y) % 3 == 1 ? Fixtures.BLUE : Fixtures.GREEN));
        boolean[] m = Background.mask(img, Background.Mode.AUTO, 0.08);
        for (boolean b : m) assertThat(b).isTrue();
    }
}
