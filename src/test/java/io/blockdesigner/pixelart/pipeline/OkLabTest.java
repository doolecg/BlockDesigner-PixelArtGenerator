package io.blockdesigner.pixelart.pipeline;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OkLabTest {

    @Test
    void referenceValues() {
        float[] white = OkLab.fromArgb(0xFFFFFFFF);
        assertThat(white[0]).isCloseTo(1f, within(1e-3f));
        assertThat(white[1]).isCloseTo(0f, within(1e-3f));
        assertThat(white[2]).isCloseTo(0f, within(1e-3f));
        assertThat(OkLab.fromArgb(0xFF000000)[0]).isCloseTo(0f, within(1e-6f));
        // sRGB red, as published with OKLab.
        float[] red = OkLab.fromArgb(0xFFFF0000);
        assertThat(red[0]).isCloseTo(0.62796f, within(1e-3f));
        assertThat(red[1]).isCloseTo(0.22486f, within(1e-3f));
        assertThat(red[2]).isCloseTo(0.12585f, within(1e-3f));
    }

    @Test
    void roundTrips() {
        SplittableRandom r = new SplittableRandom(1);
        for (int i = 0; i < 2000; i++) {
            int c = 0xFF000000 | r.nextInt(0x1000000);
            assertThat(OkLab.toArgb(OkLab.fromArgb(c))).isEqualTo(c);
        }
    }
}
