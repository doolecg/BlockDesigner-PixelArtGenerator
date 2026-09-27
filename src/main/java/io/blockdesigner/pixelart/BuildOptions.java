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
            .group("Shape")
            .choice("mode", "Shape", Arrays.stream(BuildMode.values()).map(m -> m.label).toList(), BuildMode.FLAT.label)
            .integer("width", "Width", 64, 4, MAX_SIZE).unit("blocks")
            .help("The picture is scaled to this many blocks across.")
            .choice("orientation", "Build as", Arrays.stream(Orientation.values()).map(o -> o.label).toList(), Orientation.SOUTH.label)
            .choice("heightFrom", "Height from", Arrays.stream(ReliefShape.Source.values()).map(x -> x.label).toList(), ReliefShape.Source.BRIGHTNESS.label)
            .showWhen("mode", RELIEF)
            .file("depthImage", "Depth image", List.of("png", "jpg", "jpeg", "bmp", "gif"))
            .showWhen("mode", RELIEF).showWhen("heightFrom", ReliefShape.Source.DEPTH_IMAGE.label)
            .help("A grey picture of the same shape: light is high.")
            .toggle("invert", "Dark is high", false)
            .showWhen("mode", RELIEF)
            .integer("maxHeight", "Highest point", 8, 1, 128).unit("blocks")
            .showWhen("mode", RELIEF)
            .integer("base", "Lowest point", 1, 0, 64).unit("blocks")
            .showWhen("mode", RELIEF)
            .integer("smoothing", "Smoothing", 1, 0, 10).unit("px")
            .showWhen("mode", RELIEF)
            .integer("terraces", "Terraces", 0, 0, 32)
            .showWhen("mode", RELIEF)
            .help("Steps in the height; 0 keeps a smooth slope.")
            .integer("thickness", "Thickness", 4, 1, 64).unit("blocks")
            .showWhen("mode", EXTRUDE)
            .integer("bevel", "Bevel", 0, 0, 16).unit("half blocks")
            .showWhen("mode", EXTRUDE)
            .decimal("puffiness", "Puffiness", 1, 0.2, 3)
            .showWhen("mode", INFLATE)
            .toggle("hollow", "Hollow", false)
            .showWhen("mode", RELIEF, EXTRUDE, INFLATE, REVOLVE)
            .help("Only a one-block shell, for fewer blocks.")
            .toggle("slabs", "Slabs for half steps", true)
            .showWhen("mode", RELIEF, EXTRUDE, INFLATE, REVOLVE)
            .toggle("stairs", "Stairs on slopes", true)
            .showWhen("mode", RELIEF, EXTRUDE, INFLATE, REVOLVE)
            .group("Blocks")
            .choice("blocks", "Blocks", BlockSets.NAMES, BlockSets.COLOURS)
            .text("custom", "Custom blocks", "white_concrete, gray_concrete, black_concrete")
            .showWhen("blocks", BlockSets.CUSTOM)
            .help("Block ids, separated by commas.")
            .choice("method", "Match", Arrays.stream(BlockGrid.Method.values()).map(m -> m.label).toList(), BlockGrid.Method.PIXELS.label)
            .integer("colours", "Colour groups", 16, 2, 64)
            .help("How many colours the picture is reduced to before matching.")
            .integer("minRegion", "Smallest shape", 4, 1, 1000).unit("px")
            .showWhen("method", BlockGrid.Method.REGIONS.label)
            .integer("maxBlocks", "Most kinds of block", 0, 0, 64)
            .help("0: any number of kinds.")
            .decimal("plain", "Prefer plain blocks", 0.5, 0, 2)
            .help("Higher picks plain-looking blocks over patterned ones of a closer colour.")
            .group("Background")
            .choice("background", "Background", BACKGROUNDS, "Auto")
            .help("What counts as background and is left out.")
            .decimal("tolerance", "Tolerance", 0.08, 0, 0.4).unit("%")
            .showWhen("background", "Auto", "Border colour")
            .help("How close to the background colour a pixel must be to be left out.")
            .toggle("trim", "Trim transparent border", true)
            .advanced("Picture adjustments")
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
