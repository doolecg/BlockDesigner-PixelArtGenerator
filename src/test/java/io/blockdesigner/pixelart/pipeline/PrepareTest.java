package io.blockdesigner.pixelart.pipeline;

import io.blockdesigner.pixelart.Fixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PrepareTest {

    private static Prepare.Settings width(int w) {
        return new Prepare.Settings(w, 512, true, 0, 1, 1);
    }

    @Test
    void keepsTheAspectRatio() {
        LabImage img = Prepare.run(Fixtures.picture(200, 100, (x, y) -> Fixtures.RED), width(40));
        assertThat(img.width()).isEqualTo(40);
        assertThat(img.height()).isEqualTo(20);
    }

    @Test
    void capsTheHeight() {
        LabImage img = Prepare.run(Fixtures.picture(10, 1000, (x, y) -> Fixtures.RED), new Prepare.Settings(100, 50, false, 0, 1, 1));
        assertThat(img.height()).isEqualTo(50);
        assertThat(img.width()).isEqualTo(1);
    }

    @Test
    void averagesInLinearLight() {
        // Black and white stripes one pixel wide average to half the light (sRGB about 188), not sRGB 128.
        LabImage img = Prepare.run(Fixtures.picture(64, 64, (x, y) -> x % 2 == 0 ? Fixtures.BLACK : Fixtures.WHITE), width(8));
        int grey = OkLab.toArgb(img.lab(0)) & 0xFF;
        assertThat(grey).isBetween(186, 190);
    }

    @Test
    void trimsTheTransparentBorder() {
        Picture p = Fixtures.picture(30, 30, (x, y) -> x >= 10 && x < 20 && y >= 5 && y < 25 ? Fixtures.RED : 0);
        Picture t = Prepare.trim(p);
        assertThat(t.width()).isEqualTo(10);
        assertThat(t.height()).isEqualTo(20);
    }

    @Test
    void upscalesPixelArtWithoutBlending() {
        LabImage img = Prepare.run(Fixtures.picture(2, 1, (x, y) -> x == 0 ? Fixtures.RED : Fixtures.BLUE), width(8));
        assertThat(img.height()).isEqualTo(4);
        assertThat(OkLab.toArgb(img.lab(0))).isEqualTo(Fixtures.RED);
        assertThat(OkLab.toArgb(img.lab(7))).isEqualTo(Fixtures.BLUE);
    }

    @Test
    void adjustsBrightness() {
        LabImage img = Prepare.run(Fixtures.picture(4, 4, (x, y) -> 0xFF808080), new Prepare.Settings(4, 512, false, 0.2, 1, 1));
        LabImage same = Prepare.run(Fixtures.picture(4, 4, (x, y) -> 0xFF808080), width(4));
        assertThat(img.lab()[0]).isCloseTo(same.lab()[0] + 0.1f, within(1e-4f));
    }
}
