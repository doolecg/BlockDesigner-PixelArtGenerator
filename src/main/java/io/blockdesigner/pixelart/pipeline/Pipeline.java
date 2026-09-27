package io.blockdesigner.pixelart.pipeline;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.pixelart.palette.BlockColours;
import io.blockdesigner.pixelart.palette.BlockGrid;
import io.blockdesigner.pixelart.palette.BlockSets;
import io.blockdesigner.pixelart.palette.Matcher;
import io.blockdesigner.pixelart.palette.ShapeBlocks;
import io.blockdesigner.pixelart.shape.BuildMode;
import io.blockdesigner.pixelart.shape.FlatShape;
import io.blockdesigner.pixelart.shape.HalfField;
import io.blockdesigner.pixelart.shape.Orientation;
import io.blockdesigner.pixelart.shape.ReliefShape;
import io.blockdesigner.pixelart.shape.SolidShapes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Picture to blocks, stage by stage: prepare (scale, adjust) → segment (background, colour regions) → match blocks →
 * shape. Each stage's result is kept with the settings it was made from, so changing a later setting (the block set,
 * the orientation) reruns only the stages after it. Not thread safe: run one build at a time.
 */
public final class Pipeline {

    /** Everything a build depends on, apart from the picture. */
    public record Settings(Prepare.Settings prepare, Segment segment, Match match, Orientation orientation, Shape shape) {
        /** Flat pixel art. */
        public Settings(Prepare.Settings prepare, Segment segment, Match match, Orientation orientation) {
            this(prepare, segment, match, orientation, Shape.FLAT);
        }
    }

    /**
     * The build's shape.
     *
     * @param depthImage the depth image for {@link ReliefShape.Source#DEPTH_IMAGE}, or null
     * @param slabs      use slabs for half-block detail
     * @param stairs     use stairs for half-block detail
     * @param thickness  an extrusion's depth, in blocks
     * @param bevel      an extrusion's bevel, in half blocks
     * @param puffiness  how round an inflated shape is
     */
    public record Shape(BuildMode mode, ReliefShape.Settings relief, Path depthImage, boolean slabs, boolean stairs,
                        int thickness, int bevel, double puffiness) {
        public static final Shape FLAT = new Shape(BuildMode.FLAT,
                new ReliefShape.Settings(ReliefShape.Source.BRIGHTNESS, false, 8, 1, 1, 0, false), null, true, true, 4, 0, 1);
    }

    /** Background and colour regions. */
    public record Segment(Background.Mode background, double tolerance, int colours, int minRegion) {
    }

    /** Which blocks, and how pixels pick them. */
    public record Match(String blockSet, String customBlocks, BlockGrid.Method method, int maxBlocks, double plainBlocks) {
    }

    /** The stages' results, for previews and building. */
    public record Result(LabImage prepared, Segmentation segmentation, BlockGrid grid, Structure structure) {
    }

    private static final long SEED = 20260927L;

    private final BlockColours.Source colours;
    private final Predicate<String> exists;
    private final Function<String, BlockState> resolve;
    private final ShapeBlocks.Families families;

    private Picture picture;
    private Prepare.Settings preparedWith;
    private LabImage prepared;
    private Segment segmentedWith;
    private Segmentation segmentation;
    private Object matchedWith;
    private BlockGrid grid;
    private Object shapedWith;
    private Structure structure;
    private Path depthPath;
    private long depthStamp;
    private Picture depthPicture;

    /**
     * @param colours how blocks look
     * @param exists  whether a block id is in the loaded game
     * @param resolve turns user text into a block (for the custom list); throws IllegalArgumentException when it can't
     */
    public Pipeline(BlockColours.Source colours, Predicate<String> exists, Function<String, BlockState> resolve) {
        this(colours, exists, resolve, (block, shape) -> Optional.empty());
    }

    /** @param families each block's slab and stair, for half-block detail */
    public Pipeline(BlockColours.Source colours, Predicate<String> exists, Function<String, BlockState> resolve, ShapeBlocks.Families families) {
        this.colours = colours;
        this.exists = exists;
        this.resolve = resolve;
        this.families = families;
    }

    public Result run(Picture pic, Settings s) {
        if (pic != picture) {
            picture = pic;
            preparedWith = null;
        }
        if (!s.prepare().equals(preparedWith)) {
            prepared = Prepare.run(pic, s.prepare());
            preparedWith = s.prepare();
            segmentedWith = null;
        }
        if (!s.segment().equals(segmentedWith)) {
            Segment g = s.segment();
            boolean[] mask = Background.mask(prepared, g.background(), g.tolerance());
            segmentation = Segmentation.run(prepared, mask, g.colours(), g.minRegion(), SEED);
            segmentedWith = g;
            matchedWith = null;
        }
        // The matched faces depend on whether the picture lies flat (top faces) or stands up (sides).
        List<Object> matchKey = List.of(s.match(), s.orientation().floor());
        if (!matchKey.equals(matchedWith)) {
            Matcher m = new Matcher(blocks(s.match()), colours, s.orientation().floor(), s.match().plainBlocks());
            grid = BlockGrid.match(prepared, segmentation, m, s.match().method(), s.match().maxBlocks());
            matchedWith = matchKey;
            shapedWith = null;
        }
        List<Object> shapeKey = List.of(s.shape(), s.orientation(), depthStamp(s.shape()));
        if (!shapeKey.equals(shapedWith)) {
            structure = shape(s);
            shapedWith = shapeKey;
        }
        return new Result(prepared, segmentation, grid, structure);
    }

    private Structure shape(Settings s) {
        Shape sh = s.shape();
        if (sh.mode() == BuildMode.FLAT) return FlatShape.build(grid, s.orientation());
        ShapeBlocks shapes = new ShapeBlocks(families, blocks(s.match()), BlockSets.blocks(BlockSets.BUILDING, exists), colours, s.orientation().floor());
        boolean fine = sh.slabs() || sh.stairs();
        boolean[] mask = segmentation.mask();
        int w = prepared.width(), h = prepared.height();
        HalfField field = switch (sh.mode()) {
            case RELIEF -> ReliefShape.field(prepared, segmentation, depthImage(sh), sh.relief(), fine);
            case EXTRUDE -> SolidShapes.extrude(mask, w, h, sh.thickness(), sh.bevel());
            case INFLATE -> SolidShapes.inflate(mask, w, h, sh.puffiness());
            case REVOLVE -> SolidShapes.revolve(mask, w, h);
            case FLAT -> throw new IllegalStateException();
        };
        // The relief hollows itself (under its surface only); the solids hollow on every side.
        if (sh.relief().hollow() && sh.mode() != BuildMode.RELIEF) field = HalfField.hollow(field, 2);
        return HalfField.build(field, grid, s.orientation(), shapes, sh.slabs(), sh.stairs());
    }

    /** When the depth image file was last changed (so an edited file is read again); 0 when none is used. */
    private long depthStamp(Shape sh) {
        if (sh.mode() != BuildMode.RELIEF || sh.relief().source() != ReliefShape.Source.DEPTH_IMAGE || sh.depthImage() == null) return 0;
        try {
            return Files.getLastModifiedTime(sh.depthImage()).toMillis();
        } catch (IOException e) {
            return -1;
        }
    }

    /** The depth image scaled to the build's size, or null when the relief doesn't use one. */
    private LabImage depthImage(Shape sh) {
        if (sh.relief().source() != ReliefShape.Source.DEPTH_IMAGE) return null;
        if (sh.depthImage() == null) throw new IllegalArgumentException("Pick a depth image (light is high, dark is low)");
        long stamp = depthStamp(sh);
        if (!sh.depthImage().equals(depthPath) || stamp != depthStamp) {
            try {
                depthPicture = Picture.read(sh.depthImage());
            } catch (IOException e) {
                throw new UncheckedIOException("Couldn't read the depth image: " + e.getMessage(), e);
            }
            depthPath = sh.depthImage();
            depthStamp = stamp;
        }
        return Prepare.resample(depthPicture, prepared.width(), prepared.height());
    }

    /** The blocks of the chosen set that exist in the loaded game, or the custom list. */
    List<BlockState> blocks(Match m) {
        if (!BlockSets.CUSTOM.equals(m.blockSet())) {
            List<BlockState> out = BlockSets.blocks(m.blockSet(), exists);
            if (out.isEmpty()) throw new IllegalArgumentException("None of the " + m.blockSet() + " blocks exist in the loaded Minecraft version");
            return out;
        }
        List<BlockState> out = new ArrayList<>();
        for (String part : Objects.requireNonNullElse(m.customBlocks(), "").split("[,;\\s]+")) {
            if (part.isBlank()) continue;
            try {
                out.add(resolve.apply(part.strip()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown block in the custom list: " + part.strip());
            }
        }
        if (out.isEmpty()) throw new IllegalArgumentException("The custom block list is empty");
        return out;
    }
}
