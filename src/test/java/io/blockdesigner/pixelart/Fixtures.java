package io.blockdesigner.pixelart;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.pixelart.palette.BlockColours;
import io.blockdesigner.pixelart.pipeline.OkLab;
import io.blockdesigner.pixelart.pipeline.Picture;

import java.util.Map;
import java.util.function.IntBinaryOperator;

/** Pictures drawn in code and blocks with made-up colours, so tests need neither files nor Minecraft's assets. */
public final class Fixtures {
    public static final int RED = 0xFFE02020, BLUE = 0xFF2040E0, WHITE = 0xFFFFFFFF, BLACK = 0xFF000000, GREEN = 0xFF20C040;

    /** Concrete in flat colours. */
    public static final Map<String, Integer> COLOURS = Map.of(
            "minecraft:red_concrete", RED, "minecraft:blue_concrete", BLUE, "minecraft:white_concrete", WHITE,
            "minecraft:black_concrete", BLACK, "minecraft:green_concrete", GREEN, "minecraft:gray_concrete", 0xFF808080);

    private Fixtures() {
    }

    public static Picture picture(int w, int h, IntBinaryOperator pixel) {
        int[] argb = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) argb[y * w + x] = pixel.applyAsInt(x, y);
        return new Picture(w, h, argb);
    }

    /** A disc of {@code inside} on {@code outside}. */
    public static Picture disc(int size, int inside, int outside) {
        double r = size / 2.0 - 1, c = (size - 1) / 2.0;
        return picture(size, size, (x, y) -> Math.hypot(x - c, y - c) <= r ? inside : outside);
    }

    public static BlockColours.Source source() {
        return b -> {
            Integer c = COLOURS.get(b.name());
            float[] lab = OkLab.fromArgb(c == null ? 0xFF808080 : c);
            return new BlockColours.Look(lab, lab, c == null ? 0.1f : 0f, false);
        };
    }

    public static boolean exists(String id) {
        return COLOURS.containsKey(id);
    }

    public static BlockState resolve(String text) {
        BlockState b = BlockState.parse(text);
        if (!COLOURS.containsKey(b.name())) throw new IllegalArgumentException("no such block " + text);
        return b;
    }
}
