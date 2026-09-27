package io.blockdesigner.pixelart.ui;

import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Draws {@link Options} as controls inside a plugin panel, the way BlockDesigner draws them in dialogs (the API only
 * draws options for tools, transforms and importers). Handles whole numbers, decimals, toggles, choices and text,
 * and follows {@link Options.Builder#showWhen}. Calls back after every change with the new values.
 */
final class OptionsForm {
    private final Options options;
    private final Consumer<OptionValues> onChange;
    private final Map<String, Node> rows = new LinkedHashMap<>();
    private final VBox root = new VBox(6);
    private OptionValues values;

    OptionsForm(Options options, OptionValues start, Consumer<OptionValues> onChange) {
        this.options = options;
        this.values = start;
        this.onChange = onChange;
        for (Options.Option o : options.all()) {
            Node control = control(o);
            if (control == null) continue;
            Node row = o instanceof Options.ToggleOption ? control : labelled(o.label(), control);
            rows.put(o.key(), row);
            root.getChildren().add(row);
        }
        updateVisibility();
    }

    Node node() {
        return root;
    }

    OptionValues values() {
        return values;
    }

    private void set(String key, Object value) {
        OptionValues next = values.with(key, value);
        if (next.equals(values)) return;
        values = next;
        updateVisibility();
        onChange.accept(values);
    }

    private void updateVisibility() {
        rows.forEach((key, row) -> {
            boolean shown = options.shown(key, values);
            row.setVisible(shown);
            row.setManaged(shown);
        });
    }

    private static Node labelled(String label, Node control) {
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
        l.setMinWidth(Region.USE_PREF_SIZE);
        VBox box = new VBox(2, l, control);
        if (control instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private Node control(Options.Option o) {
        String key = o.key();
        return switch (o) {
            case Options.IntegerOption i -> {
                Spinner<Integer> s = new Spinner<>(i.min(), i.max(), values.integer(key));
                s.setEditable(true);
                s.valueProperty().addListener((obs, a, b) -> {
                    if (b != null) set(key, b);
                });
                // Typing a number counts without pressing Enter.
                s.getEditor().textProperty().addListener((obs, a, b) -> {
                    try {
                        int v = Integer.parseInt(b.strip());
                        if (v >= i.min() && v <= i.max()) s.getValueFactory().setValue(v);
                    } catch (NumberFormatException ignored) {
                    }
                });
                yield s;
            }
            case Options.DecimalOption d -> {
                Slider s = new Slider(d.min(), d.max(), values.decimal(key));
                Label shown = new Label(format(values.decimal(key)));
                shown.setMinWidth(36);
                shown.setAlignment(Pos.CENTER_RIGHT);
                s.valueProperty().addListener((obs, a, b) -> shown.setText(format(b.doubleValue())));
                // Only after the thumb is let go (or a click/key), so dragging doesn't rebuild on every pixel.
                s.valueChangingProperty().addListener((obs, was, now) -> {
                    if (!now) set(key, s.getValue());
                });
                s.valueProperty().addListener((obs, a, b) -> {
                    if (!s.isValueChanging()) set(key, b.doubleValue());
                });
                // Double-click puts it back to its default.
                s.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) s.setValue(d.defaultValue());
                });
                HBox.setHgrow(s, Priority.ALWAYS);
                HBox box = new HBox(6, s, shown);
                box.setAlignment(Pos.CENTER_LEFT);
                yield box;
            }
            case Options.ToggleOption t -> {
                CheckBox c = new CheckBox(t.label());
                c.setSelected(values.toggle(key));
                c.selectedProperty().addListener((obs, a, b) -> set(key, b));
                yield c;
            }
            case Options.ChoiceOption c -> {
                ComboBox<String> box = new ComboBox<>();
                box.getItems().setAll(c.values());
                box.setValue(values.choice(key));
                box.valueProperty().addListener((obs, a, b) -> {
                    if (b != null) set(key, b);
                });
                yield box;
            }
            case Options.TextOption t -> {
                TextField f = new TextField(values.text(key));
                f.setOnAction(e -> set(key, f.getText()));
                f.focusedProperty().addListener((obs, was, now) -> {
                    if (!now) set(key, f.getText());
                });
                yield f;
            }
            case Options.FileOption fo -> {
                TextField f = new TextField(values.file(key).map(p -> p.getFileName().toString()).orElse(""));
                f.setEditable(false);
                f.setPromptText("No file");
                values.file(key).ifPresent(p -> f.setTooltip(new javafx.scene.control.Tooltip(p.toString())));
                Button browse = new Button("Browse…");
                browse.setOnAction(e -> {
                    FileChooser fc = new FileChooser();
                    fc.setTitle(fo.label());
                    if (!fo.extensions().isEmpty())
                        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(fo.label(), fo.extensions().stream().map(x -> "*." + x).toList()));
                    values.file(key).map(Path::getParent).filter(Files::isDirectory).ifPresent(d -> fc.setInitialDirectory(d.toFile()));
                    java.io.File picked = fc.showOpenDialog(browse.getScene() == null ? null : browse.getScene().getWindow());
                    if (picked == null) return;
                    f.setText(picked.getName());
                    f.setTooltip(new javafx.scene.control.Tooltip(picked.getPath()));
                    set(key, picked.toPath());
                });
                HBox.setHgrow(f, Priority.ALWAYS);
                HBox box = new HBox(6, f, browse);
                box.setAlignment(Pos.CENTER_LEFT);
                yield box;
            }
            default -> null;
        };
    }

    private static String format(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
