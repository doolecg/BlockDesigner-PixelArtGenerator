package io.blockdesigner.pixelart.ui;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.pixelart.BuildOptions;
import io.blockdesigner.pixelart.palette.BlockColours;
import io.blockdesigner.pixelart.palette.BlockGrid;
import io.blockdesigner.pixelart.palette.CatalogFamilies;
import io.blockdesigner.pixelart.pipeline.Picture;
import io.blockdesigner.pixelart.pipeline.Pipeline;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.PanelContext;
import io.blockdesigner.plugin.PluginContext;
import io.blockdesigner.plugin.PluginPanel;
import io.blockdesigner.plugin.ui.ActionBar;
import io.blockdesigner.plugin.ui.Banner;
import io.blockdesigner.plugin.ui.Controls;
import io.blockdesigner.plugin.ui.EmptyState;
import io.blockdesigner.plugin.ui.Icon;
import io.blockdesigner.plugin.ui.ItemList;
import io.blockdesigner.plugin.ui.ItemRow;
import io.blockdesigner.plugin.ui.OptionsForm;
import io.blockdesigner.plugin.ui.PanelScaffold;
import io.blockdesigner.plugin.ui.Section;
import io.blockdesigner.plugin.ui.Segmented;
import io.blockdesigner.plugin.ui.Theme;
import io.blockdesigner.plugin.ui.Tone;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Generate page: open a picture, see it as colour regions and as blocks while changing the settings, then build it
 * as a new layer or place it with the tool. The settings are the Import window's too (one set of values). The work
 * runs on a background thread; only the newest request's result is shown.
 */
public final class PixelArtPanel implements PluginPanel {
    private static final String[] EXTENSIONS = {"png", "jpg", "jpeg", "bmp", "gif"};
    private static final List<String> VIEWS = List.of("Picture", "Regions", "Blocks");
    /** The values' name in BlockDesigner's settings: the importer's, so this page and File › Import agree. */
    static final String REMEMBER_AS = "importer/image";

    /** A kind of block the build uses, and how many. */
    private record Used(BlockState block, String name, long count, Image icon, int colour) {
    }

    private final PluginContext ctx;
    private final BlockColours colours;
    private final CurrentBuild current;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Pixel Art Generator");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger generation = new AtomicInteger();
    private volatile Pipeline pipeline;

    // Only touched on the JavaFX thread.
    private Path file;
    private Picture picture;
    private Pipeline.Result result;
    private OptionValues values;
    private boolean assetsSeen;
    private Label fileLabel, stats;
    private ImageView view;
    private Segmented<String> preview;
    private VBox pictureBox;
    private EmptyState noPicture;
    private ItemList<Used> blocks;
    private Banner banner;
    private Button build, place, copy;

    public PixelArtPanel(PluginContext ctx, BlockColours colours, CurrentBuild current) {
        this.ctx = ctx;
        this.colours = colours;
        this.current = current;
    }

    @Override
    public String id() {
        return "pixel-art-generator";
    }

    @Override
    public String title() {
        return "Generate";
    }

    @Override
    public String icon() {
        // A picture frame with a hill, turning into two blocks.
        return "M2 3 H10 V9 H2 Z M2 8 L5 5.5 L7 7 L10 5 M11 10 H14 V13 H11 Z M8 12 H11 V15 H8 Z";
    }

    @Override
    public Node create(PanelContext context) {
        assetsSeen = ctx.assets().available();
        context.onShown(() -> {
            // Block colours come from the game's textures: rebuild once they are loaded.
            boolean now = ctx.assets().available();
            if (now != assetsSeen) {
                assetsSeen = now;
                pipeline = null;
                rebuild();
            }
        });

        // The settings: the app's form, sharing its values with File › Import.
        OptionsForm form = ctx.ui().optionsForm(BuildOptions.OPTIONS, REMEMBER_AS, v -> {
            values = v;
            rebuild();
        });
        values = form.values();
        Optional<OptionValues> old = OldPanelSettings.migrate(ctx.dataFolder(), ctx.blocks()::resolve);
        if (old.isPresent()) {
            form.setValues(old.get());
            values = form.values();
        }

        // The picture, at the top: an empty state until one is open.
        noPicture = new EmptyState(Icon.IMAGE, "No picture open.")
                .hint("Open a picture or drop one here.")
                .action(Controls.primary("Open picture…", this::choose));
        fileLabel = Controls.pathCaption("");
        HBox.setHgrow(fileLabel, javafx.scene.layout.Priority.ALWAYS);
        fileLabel.setMaxWidth(Double.MAX_VALUE);
        HBox fileRow = new HBox(Theme.SM, fileLabel, Controls.iconButton(Icon.FOLDER, "Open another picture…", this::choose));
        fileRow.setAlignment(Pos.CENTER_LEFT);
        preview = new Segmented<>(VIEWS, s -> s);
        preview.setValue("Blocks");
        preview.valueProperty().addListener((o, a, b) -> showPreview());
        view = new ImageView();
        view.setPreserveRatio(true);
        view.setSmooth(false); // blocks stay crisp squares when scaled up
        StackPane frame = new StackPane(view);
        frame.getStyleClass().add("bd-frame");
        view.fitWidthProperty().bind(frame.widthProperty().subtract(16));
        view.fitHeightProperty().bind(frame.heightProperty().subtract(16));
        pictureBox = new VBox(Theme.SM, fileRow, preview, frame);
        Controls.show(pictureBox, false);

        // Blocks used.
        blocks = new ItemList<Used>(u -> (u.icon() != null ? ItemRow.of(u.name()).image(u.icon()) : ItemRow.of(u.name()).swatch(u.colour()))
                .trailing(Controls.caption(String.format(Locale.ROOT, "%,d", u.count()))))
                .empty(new EmptyState(null, "No blocks yet.").hint("Open a picture to see the blocks it takes."))
                .visibleRows(3, 10);
        copy = Controls.iconButton(Icon.COPY, "Copy block list", this::copyList);
        Section used = new Section("Blocks used", blocks).actions(copy);

        // Status and actions, at the bottom.
        banner = new Banner();
        stats = Controls.caption("");
        stats.setWrapText(true);
        build = Controls.primary("Build as new layer", this::buildLayer);
        place = Controls.button("Place with tool", "Pick the Place pixel art tool: click where the build goes", () -> ctx.pickTool(PlacePixelArtTool.ID));
        build.setDisable(true);
        place.setDisable(true);
        copy.setDisable(true);

        PanelScaffold page = new PanelScaffold()
                .top(noPicture, pictureBox)
                .add(form.node(), used)
                .footer(banner, stats, new ActionBar(build, place));
        // The preview takes about a third of the page, 140 to 260 px.
        frame.prefHeightProperty().bind(Bindings.createDoubleBinding(() -> Math.clamp(page.getHeight() * 0.3, 140, 260), page.heightProperty()));
        frame.setMinHeight(140);
        frame.setMaxHeight(260);
        Controls.show(stats, false);

        page.setOnDragOver(e -> {
            if (droppedImage(e.getDragboard().getFiles()) != null) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        page.setOnDragDropped(e -> {
            Path p = droppedImage(e.getDragboard().getFiles());
            if (p != null) open(p);
            e.setDropCompleted(p != null);
            e.consume();
        });
        return page;
    }

    @Override
    public void dispose() {
        worker.shutdownNow();
    }

    // ---- picture and building --------------------------------------------------------------------------------

    private void choose() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open a picture");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pictures", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
        if (file != null && file.getParent() != null && Files.isDirectory(file.getParent())) fc.setInitialDirectory(file.getParent().toFile());
        java.io.File f = fc.showOpenDialog(ctx.ui().owner());
        if (f != null) open(f.toPath());
    }

    private void open(Path p) {
        banner.hide();
        worker.submit(() -> {
            try {
                Picture pic = Picture.read(p);
                ctx.runOnUiThread(() -> {
                    file = p;
                    picture = pic;
                    fileLabel.setText(p.getFileName() + " · " + pic.width() + " × " + pic.height() + " px");
                    Controls.show(noPicture, false);
                    Controls.show(pictureBox, true);
                    rebuild();
                });
            } catch (IOException | RuntimeException ex) {
                ctx.runOnUiThread(() -> banner.show(Tone.DANGER, "Couldn't read " + p.getFileName() + ": " + ex.getMessage(),
                        "Open another…", this::choose));
            }
        });
    }

    private void rebuild() {
        if (picture == null) return;
        Picture pic = picture;
        Pipeline.Settings settings = BuildOptions.settings(values);
        int gen = generation.incrementAndGet();
        stats.setText("Working…");
        Controls.show(stats, true);
        worker.submit(() -> {
            // A newer request is already queued: skip this one.
            if (gen != generation.get()) return;
            try {
                if (pipeline == null) pipeline = new Pipeline(colours.source(), ctx.blocks()::exists, ctx.blocks()::resolve, CatalogFamilies.of(ctx.blocks()));
                Pipeline.Result r = pipeline.run(pic, settings);
                ctx.runOnUiThread(() -> {
                    if (gen == generation.get()) show(r);
                });
            } catch (RuntimeException ex) {
                ctx.runOnUiThread(() -> {
                    if (gen != generation.get()) return;
                    result = null;
                    current.set(null, null);
                    Controls.show(stats, false);
                    banner.show(Tone.DANGER, ex.getMessage() == null ? ex.toString() : ex.getMessage());
                    build.setDisable(true);
                    place.setDisable(true);
                    copy.setDisable(true);
                    blocks.getItems().clear();
                });
            }
        });
    }

    private void show(Pipeline.Result r) {
        result = r;
        banner.hide();
        BlockGrid g = r.grid();
        long total = r.structure().blockCount();
        stats.setText(String.format(Locale.ROOT, "%d × %d blocks · %,d blocks · %d kinds · %d colour regions%s",
                g.width(), g.height(), total, g.palette().size(), r.segmentation().regions().length,
                ctx.assets().available() ? "" : " · load Minecraft's assets for exact block colours"));
        build.setDisable(total == 0);
        place.setDisable(total == 0);
        copy.setDisable(total == 0);
        current.set(total == 0 ? null : r.structure(), file.getFileName().toString());
        showPreview();
        showBlocks(g);
    }

    private void showPreview() {
        if (result == null && picture == null) return;
        String which = preview.getValue();
        Image img;
        if (which.equals("Picture") || result == null) img = image(picture.width(), picture.height(), picture.argb());
        else if (which.equals("Regions")) img = image(result.segmentation().width(), result.segmentation().height(), result.segmentation().toArgb());
        else img = image(result.grid().width(), result.grid().height(), result.grid().toArgb());
        view.setImage(img);
        view.setSmooth(which.equals("Picture"));
    }

    private void showBlocks(BlockGrid g) {
        long[] counts = g.counts();
        List<Used> out = new ArrayList<>();
        for (int k = 0; k < g.palette().size(); k++) {
            BlockState b = g.palette().get(k);
            out.add(new Used(b, ctx.blocks().displayName(b), counts[k], ctx.blockIcon(b).orElse(null), ctx.blocks().averageColor(b)));
        }
        out.sort((a, b) -> Long.compare(b.count(), a.count()));
        blocks.getItems().setAll(out);
    }

    private void buildLayer() {
        if (result == null || file == null) return;
        ctx.addLayer("Pixel art: " + file.getFileName(), result.structure().copy());
        ctx.toast(String.format(Locale.ROOT, "Built %s: %,d blocks", file.getFileName(), result.structure().blockCount()));
    }

    private void copyList() {
        if (result == null) return;
        BlockGrid g = result.grid();
        long[] counts = g.counts();
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < counts.length; k++)
            sb.append(ctx.blocks().displayName(g.palette().get(k))).append(": ").append(counts[k]).append('\n');
        ctx.ui().copyText(sb.toString(), "Block list copied");
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static Image image(int w, int h, int[] argb) {
        WritableImage img = new WritableImage(w, h);
        img.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        return img;
    }

    private static Path droppedImage(java.util.List<java.io.File> files) {
        if (files == null || files.size() != 1) return null;
        String name = files.getFirst().getName().toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) if (name.endsWith("." + ext)) return files.getFirst().toPath();
        return null;
    }
}
