package io.blockdesigner.pixelart;

import io.blockdesigner.pixelart.palette.BlockColours;
import io.blockdesigner.pixelart.palette.BlockGrid;
import io.blockdesigner.pixelart.palette.BlockSets;
import io.blockdesigner.pixelart.pipeline.OkLab;
import io.blockdesigner.pixelart.pipeline.Picture;
import io.blockdesigner.pixelart.pipeline.Pipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes previews to check by eye:  IMAGE_SHOTS=1 ./gradlew test --tests "*ShotsTest*"  then look in
 * build/pixel-art-shots. Uses the concrete colours of vanilla Minecraft (approximate texture averages).
 */
@EnabledIfEnvironmentVariable(named = "IMAGE_SHOTS", matches = "1")
class ShotsTest {
    private static final Map<String, Integer> CONCRETE = Map.ofEntries(
            Map.entry("white", 0xCFD5D6), Map.entry("orange", 0xE06101), Map.entry("magenta", 0xA9309F),
            Map.entry("light_blue", 0x2489C7), Map.entry("yellow", 0xF1AF15), Map.entry("lime", 0x5EA918),
            Map.entry("pink", 0xD6658F), Map.entry("gray", 0x373A3E), Map.entry("light_gray", 0x7D7D73),
            Map.entry("cyan", 0x157788), Map.entry("purple", 0x64209C), Map.entry("blue", 0x2D2F8F),
            Map.entry("brown", 0x603C20), Map.entry("green", 0x495B24), Map.entry("red", 0x8E2121), Map.entry("black", 0x080A0F));

    @Test
    void writeShots() throws Exception {
        Path out = Path.of(System.getenv().getOrDefault("IMAGE_SHOTS_DIR", "build/pixel-art-shots"));
        Files.createDirectories(out);
        BlockColours.Source source = b -> {
            String dye = b.path().replace("_concrete", "");
            float[] lab = OkLab.fromArgb(CONCRETE.getOrDefault(dye, 0x808080));
            return new BlockColours.Look(lab, lab, 0, false);
        };
        Pipeline pipeline = new Pipeline(source, id -> id.endsWith("_concrete"), io.blockdesigner.core.model.BlockState::parse);
        Picture pic = sunset(256, 160);
        ImageIO.write(scale(pic.argb(), pic.width(), pic.height(), 2), "png", out.resolve("0-picture.png").toFile());
        for (BlockGrid.Method m : BlockGrid.Method.values()) {
            var v = BuildOptions.OPTIONS.defaults().with("blocks", BlockSets.CONCRETE).with("width", 96).with("method", m.label);
            Pipeline.Result r = pipeline.run(pic, BuildOptions.settings(v));
            ImageIO.write(scale(r.grid().toArgb(), r.grid().width(), r.grid().height(), 6), "png",
                    out.resolve("blocks-" + m.name().toLowerCase() + ".png").toFile());
            if (m == BlockGrid.Method.REGIONS)
                ImageIO.write(scale(r.segmentation().toArgb(), r.grid().width(), r.grid().height(), 6), "png", out.resolve("regions.png").toFile());
        }
        // A relief of a soft blob, seen from above: shaded by height, slabs lighter, stairs marked by their high side.
        Picture blob = Fixtures.picture(48, 48, (x, y) -> {
            double d = Math.hypot(x - 23.5, y - 23.5) / 24;
            int g = (int) Math.clamp(255 * (1 - d * d), 0, 255);
            return 0xFF000000 | g << 16 | g << 8 | g;
        });
        Pipeline shaped = new Pipeline(source, id -> id.endsWith("_concrete"), io.blockdesigner.core.model.BlockState::parse,
                (b, shape) -> java.util.Optional.of(b.name() + (shape == io.blockdesigner.core.blocks.BlockFamily.Shape.SLAB ? "_slab" : "_stairs")));
        var v = BuildOptions.OPTIONS.defaults().with("mode", "Relief / heightmap").with("blocks", BlockSets.CONCRETE).with("width", 48)
                .with("orientation", "Floor (top is north)").with("background", "None").with("maxHeight", 10);
        ImageIO.write(topDown(shaped.run(blob, BuildOptions.settings(v)).structure(), 12), "png", out.resolve("relief-top.png").toFile());
    }

    /** Looking straight down: brightness by height; slabs a notch lighter; a stair's high half drawn darker. */
    private static BufferedImage topDown(io.blockdesigner.core.model.Structure s, int px) {
        var b = s.bounds().orElseThrow();
        BufferedImage img = new BufferedImage(b.sizeX() * px, b.sizeZ() * px, BufferedImage.TYPE_INT_ARGB);
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int z = b.minZ(); z <= b.maxZ(); z++)
                for (int y = b.maxY(); y >= b.minY(); y--) {
                    var st = s.get(x, y, z);
                    if (st.isAir()) continue;
                    double half = st.name().endsWith("_slab") ? 0.5 : 1;
                    int g = (int) (40 + 200 * (y - b.minY() + half) / (b.sizeY() + 1));
                    for (int i = 0; i < px; i++)
                        for (int j = 0; j < px; j++) {
                            int c = g;
                            if (st.name().endsWith("_stairs")) {
                                String f = st.get("facing");
                                boolean high = switch (f) {
                                    case "north" -> j < px / 2;
                                    case "south" -> j >= px / 2;
                                    case "east" -> i >= px / 2;
                                    default -> i < px / 2;
                                };
                                c = high ? g : g - 25;
                            }
                            c = Math.clamp(c, 0, 255);
                            img.setRGB((x - b.minX()) * px + i, (z - b.minZ()) * px + j, 0xFF000000 | c << 16 | c << 8 | c);
                        }
                    break;
                }
        return img;
    }

    /** A sky gradient, a sun and dark hills: smooth colour plus hard shapes. */
    private static Picture sunset(int w, int h) {
        return Fixtures.picture(w, h, (x, y) -> {
            double t = (double) y / h;
            double hill = h * (0.62 + 0.12 * Math.sin(x * 0.035) + 0.05 * Math.sin(x * 0.11));
            if (y > hill) return 0xFF000000 | (int) (20 + 30 * t) << 16 | (int) (60 + 40 * t) << 8 | 30;
            if (Math.hypot(x - w * 0.7, y - h * 0.42) < h * 0.14) return 0xFFFFD040;
            int r = (int) (40 + 215 * t), g = (int) (60 + 60 * t), b = (int) (160 - 90 * t);
            return 0xFF000000 | r << 16 | g << 8 | b;
        });
    }

    private static BufferedImage scale(int[] argb, int w, int h, int s) {
        BufferedImage img = new BufferedImage(w * s, h * s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h * s; y++) for (int x = 0; x < w * s; x++) img.setRGB(x, y, argb[(y / s) * w + x / s]);
        return img;
    }
}
