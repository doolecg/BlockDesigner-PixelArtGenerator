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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Pixel Art Generator page: pick a picture, see it as colour regions and as blocks while changing the settings, then
 * build it as a new layer. The work runs on a background thread; only the newest request's result is shown.
 */
public final class PixelArtPanel implements PluginPanel {
    private static final String[] EXTENSIONS = {"png", "jpg", "jpeg", "bmp", "gif"};

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
    private ToggleGroup previewGroup;
    private VBox blockRows;
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
        return "Pixel Art Generator";
    }

    @Override
    public String icon() {
        // A picture frame with a hill, turning into two blocks.
        return "M2 3 H10 V9 H2 Z M2 8 L5 5.5 L7 7 L10 5 M11 10 H14 V13 H11 Z M8 12 H11 V15 H8 Z";
    }

    @Override
    public Node create(PanelContext context) {
        values = loadValues();
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

        Button open = new Button("Open picture…");
        open.setOnAction(e -> choose(open));
        fileLabel = new Label("No picture yet: open one or drop it here");
        fileLabel.setStyle("-fx-text-fill: -color-fg-muted;");
        fileLabel.setWrapText(true);

        previewGroup = new ToggleGroup();
        HBox tabs = new HBox(4, tab("Picture", "picture"), tab("Regions", "regions"), tab("Blocks", "blocks"));
        previewGroup.selectToggle(previewGroup.getToggles().get(2));
        previewGroup.selectedToggleProperty().addListener((obs, a, b) -> {
            if (b == null) previewGroup.selectToggle(a);
            else showPreview();
        });

        view = new ImageView();
        view.setPreserveRatio(true);
        view.setSmooth(false); // blocks stay crisp squares when scaled up
        StackPane frame = new StackPane(view);
        frame.setMinHeight(180);
        frame.setPadding(new Insets(6));
        frame.setStyle("-fx-background-color: -color-bg-inset; -fx-background-radius: 6;");
        view.fitWidthProperty().bind(frame.widthProperty().subtract(12));
        view.setFitHeight(320);

        stats = new Label();
        stats.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
        stats.setWrapText(true);

        build = new Button("Build as new layer");
        build.getStyleClass().add("accent");
        build.setMaxWidth(Double.MAX_VALUE);
        build.setOnAction(e -> buildLayer());
        place = new Button("Place with tool");
        place.setOnAction(e -> ctx.pickTool(PlacePixelArtTool.ID));
        copy = new Button("Copy block list");
        copy.setOnAction(e -> copyList());
        HBox.setHgrow(build, Priority.ALWAYS);
        HBox actions = new HBox(6, build, place);
        HBox more = new HBox(6, copy);
        build.setDisable(true);
        place.setDisable(true);
        copy.setDisable(true);

        OptionsForm form = new OptionsForm(BuildOptions.OPTIONS, values, v -> {
            values = v;
            saveValues();
            rebuild();
        });

        blockRows = new VBox(2);
        VBox content = new VBox(10, new HBox(8, open), fileLabel, tabs, frame, stats, actions, more,
                heading("Settings"), form.node(), heading("Blocks used"), blockRows);
        content.setPadding(new Insets(10));
        content.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        scroll.setOnDragOver(e -> {
            if (droppedImage(e.getDragboard().getFiles()) != null) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        scroll.setOnDragDropped(e -> {
            Path p = droppedImage(e.getDragboard().getFiles());
            if (p != null) open(p);
            e.setDropCompleted(p != null);
            e.consume();
        });
        return scroll;
    }

    @Override
    public void dispose() {
        worker.shutdownNow();
    }

    // ---- picture and building --------------------------------------------------------------------------------

    private void choose(Node owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open a picture");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pictures", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
        if (file != null && file.getParent() != null && Files.isDirectory(file.getParent())) fc.setInitialDirectory(file.getParent().toFile());
        java.io.File f = fc.showOpenDialog(owner.getScene() == null ? null : owner.getScene().getWindow());
        if (f != null) open(f.toPath());
    }

    private void open(Path p) {
        fileLabel.setText("Reading " + p.getFileName() + "…");
        worker.submit(() -> {
            try {
                Picture pic = Picture.read(p);
                ctx.runOnUiThread(() -> {
                    file = p;
                    picture = pic;
                    fileLabel.setText(p.getFileName() + " · " + pic.width() + " × " + pic.height() + " px");
                    rebuild();
                });
            } catch (IOException | RuntimeException ex) {
                ctx.runOnUiThread(() -> fileLabel.setText("Couldn't read " + p.getFileName() + ": " + ex.getMessage()));
            }
        });
    }

    private void rebuild() {
        if (picture == null) return;
        Picture pic = picture;
        Pipeline.Settings settings = BuildOptions.settings(values);
        int gen = generation.incrementAndGet();
        stats.setText("Working…");
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
                    stats.setText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
                    build.setDisable(true);
                    place.setDisable(true);
                    copy.setDisable(true);
                });
            }
        });
    }

    private void show(Pipeline.Result r) {
        result = r;
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
        String which = previewGroup.getSelectedToggle() == null ? "blocks" : (String) previewGroup.getSelectedToggle().getUserData();
        Image img;
        if (which.equals("picture") || result == null) img = image(picture.width(), picture.height(), picture.argb());
        else if (which.equals("regions")) img = image(result.segmentation().width(), result.segmentation().height(), result.segmentation().toArgb());
        else img = image(result.grid().width(), result.grid().height(), result.grid().toArgb());
        view.setImage(img);
        view.setSmooth(which.equals("picture"));
    }

    private void showBlocks(BlockGrid g) {
        long[] counts = g.counts();
        blockRows.getChildren().clear();
        for (int k = 0; k < g.palette().size() && k < 64; k++) {
            BlockState b = g.palette().get(k);
            Node icon = ctx.blockIcon(b).<Node>map(i -> {
                ImageView iv = new ImageView(i);
                iv.setFitWidth(16);
                iv.setFitHeight(16);
                return iv;
            }).orElseGet(() -> {
                Region sw = new Region();
                sw.setMinSize(14, 14);
                sw.setMaxSize(14, 14);
                return sw;
            });
            Label name = new Label(ctx.blocks().displayName(b));
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            Label n = new Label(String.format(Locale.ROOT, "%,d", counts[k]));
            n.setStyle("-fx-text-fill: -color-fg-muted;");
            HBox row = new HBox(6, icon, name, gap, n);
            row.setAlignment(Pos.CENTER_LEFT);
            blockRows.getChildren().add(row);
        }
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
        ClipboardContent c = new ClipboardContent();
        c.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(c);
        ctx.toast("Block list copied");
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private ToggleButton tab(String label, String id) {
        ToggleButton b = new ToggleButton(label);
        b.setUserData(id);
        b.setToggleGroup(previewGroup);
        b.getStyleClass().add("small");
        return b;
    }

    private static Label heading(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-padding: 6 0 0 0;");
        return l;
    }

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

    // ---- remembered settings ----------------------------------------------------------------------------------

    private Path settingsFile() {
        return ctx.dataFolder().resolve("panel.properties");
    }

    private OptionValues loadValues() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(settingsFile())) {
            p.load(r);
        } catch (IOException e) {
            return BuildOptions.OPTIONS.defaults();
        }
        Map<String, String> saved = new HashMap<>();
        p.stringPropertyNames().forEach(k -> saved.put(k, p.getProperty(k)));
        return OptionValues.fromStrings(BuildOptions.OPTIONS, saved, ctx.blocks());
    }

    private void saveValues() {
        Properties p = new Properties();
        p.putAll(values.toStrings());
        try (Writer w = Files.newBufferedWriter(settingsFile())) {
            p.store(w, "Pixel Art Generator panel settings");
        } catch (IOException e) {
            ctx.log("Couldn't save the panel settings: " + e.getMessage());
        }
    }
}
