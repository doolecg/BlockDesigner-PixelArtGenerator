package io.blockdesigner.pixelart;

import io.blockdesigner.pixelart.palette.BlockGrid;
import io.blockdesigner.pixelart.palette.BlockSets;
import io.blockdesigner.pixelart.pipeline.Background;
import io.blockdesigner.pixelart.pipeline.Pipeline;
import io.blockdesigner.pixelart.pipeline.Prepare;
import io.blockdesigner.pixelart.shape.BuildMode;
import io.blockdesigner.pixelart.shape.Orientation;
import io.blockdesigner.pixelart.shape.ReliefShape;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;

import java.util.Arrays;
import java.util.List;

/** The build settings, as options (the panel and the Import dialog both show them) and as pipeline settings. */
public final class BuildOptions {
    public static final int MAX_SIZE = 512;

    static final List<String> BACKGROUNDS = List.of("Auto", "Transparent pixels", "Border colour", "None");

    private static final String FLAT = BuildMode.FLAT.label, RELIEF = BuildMode.RELIEF.label, EXTRUDE = BuildMode.EXTRUDE.label,
            INFLATE = BuildMode.INFLATE.label, REVOLVE = BuildMode.REVOLVE.label;

    public static final Options OPTIONS = Options.builder()
            .choice("mode", "Shape", Arrays.stream(BuildMode.values()).map(m -> m.label).toList(), BuildMode.FLAT.label)
            .integer("width", "Width in blocks", 64, 4, MAX_SIZE)
            .choice("orientation", "Build as", Arrays.stream(Orientation.values()).map(o -> o.label).toList(), Orientation.SOUTH.label)
            .choice("heightFrom", "Height from", Arrays.stream(ReliefShape.Source.values()).map(x -> x.label).toList(), ReliefShape.Source.BRIGHTNESS.label)
            .showWhen("mode", RELIEF)
            .file("depthImage", "Depth image (light is high)", List.of("png", "jpg", "jpeg", "bmp", "gif"))
            .showWhen("mode", RELIEF).showWhen("heightFrom", ReliefShape.Source.DEPTH_IMAGE.label)
            .toggle("invert", "Dark is high", false)
            .showWhen("mode", RELIEF)
            .integer("maxHeight", "Highest point (blocks)", 8, 1, 128)
            .showWhen("mode", RELIEF)
            .integer("base", "Lowest point (blocks)", 1, 0, 64)
            .showWhen("mode", RELIEF)
            .integer("smoothing", "Smoothing (pixels)", 1, 0, 10)
            .showWhen("mode", RELIEF)
            .integer("terraces", "Terraces (0: smooth)", 0, 0, 32)
            .showWhen("mode", RELIEF)
            .integer("thickness", "Thickness (blocks)", 4, 1, 64)
            .showWhen("mode", EXTRUDE)
            .integer("bevel", "Bevel (half blocks)", 0, 0, 16)
            .showWhen("mode", EXTRUDE)
            .decimal("puffiness", "Puffiness", 1, 0.2, 3)
            .showWhen("mode", INFLATE)
            .toggle("hollow", "Hollow (a one-block shell)", false)
            .showWhen("mode", RELIEF, EXTRUDE, INFLATE, REVOLVE)
            .toggle("slabs", "Slabs for half steps", true)
            .showWhen("mode", RELIEF, EXTRUDE, INFLATE, REVOLVE)
            .toggle("stairs", "Stairs on slopes", true)
            .showWhen("mode", RELIEF, EXTRUDE, INFLATE, REVOLVE)
            .choice("blocks", "Blocks", BlockSets.NAMES, BlockSets.COLOURS)
            .text("custom", "Custom blocks (comma separated)", "white_concrete, gray_concrete, black_concrete")
            .showWhen("blocks", BlockSets.CUSTOM)
            .choice("method", "Match", Arrays.stream(BlockGrid.Method.values()).map(m -> m.label).toList(), BlockGrid.Method.PIXELS.label)
            .integer("colours", "Colour groups", 16, 2, 64)
            .integer("minRegion", "Smallest shape (pixels)", 4, 1, 1000)
            .showWhen("method", BlockGrid.Method.REGIONS.label)
            .integer("maxBlocks", "Most kinds of block (0: any)", 0, 0, 64)
            .decimal("plain", "Prefer plain-looking blocks", 0.5, 0, 2)
            .choice("background", "Background", BACKGROUNDS, "Auto")
            .decimal("tolerance", "Background tolerance", 0.08, 0, 0.4)
            .showWhen("background", "Auto", "Border colour")
            .toggle("trim", "Trim transparent border", true)
            .decimal("brightness", "Brightness", 0, -1, 1)
            .decimal("contrast", "Contrast", 1, 0, 2)
            .decimal("saturation", "Saturation", 1, 0, 2)
            .build();

    private BuildOptions() {
    }

    public static Pipeline.Settings settings(OptionValues v) {
        Background.Mode bg = switch (v.choice("background")) {
            case "Transparent pixels" -> Background.Mode.TRANSPARENT;
            case "Border colour" -> Background.Mode.BORDER;
            case "None" -> Background.Mode.NONE;
            default -> Background.Mode.AUTO;
        };
        BuildMode mode = BuildMode.of(v.choice("mode"));
        return new Pipeline.Settings(
                new Prepare.Settings(Math.min(v.integer("width"), mode.maxSize()), mode.maxSize(), v.toggle("trim"), v.decimal("brightness"), v.decimal("contrast"), v.decimal("saturation")),
                new Pipeline.Segment(bg, v.decimal("tolerance"), v.integer("colours"), v.integer("minRegion")),
                new Pipeline.Match(v.choice("blocks"), v.text("custom"), BlockGrid.Method.of(v.choice("method")), v.integer("maxBlocks"), v.decimal("plain")),
                Orientation.of(v.choice("orientation")),
                new Pipeline.Shape(mode,
                        new ReliefShape.Settings(ReliefShape.Source.of(v.choice("heightFrom")), v.toggle("invert"), v.integer("maxHeight"),
                                v.integer("base"), v.integer("smoothing"), v.integer("terraces"), v.toggle("hollow")),
                        v.file("depthImage").orElse(null), v.toggle("slabs"), v.toggle("stairs"),
                        v.integer("thickness"), v.integer("bevel"), v.decimal("puffiness")));
    }
}
