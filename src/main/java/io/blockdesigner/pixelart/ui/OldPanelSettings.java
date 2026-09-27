package io.blockdesigner.pixelart.ui;

import io.blockdesigner.pixelart.BuildOptions;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.PluginCommand;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Before 0.2.0 the page kept its settings in {@code panel.properties} in the plugin's data folder; BlockDesigner keeps
 * them now (shared with File › Import).
 */
final class OldPanelSettings {
    private OldPanelSettings() {
    }

    /** Reads that file once, if it is there, and renames it to {@code panel.properties.migrated}. */
    static Optional<OptionValues> migrate(Path dataFolder, PluginCommand.BlockResolver blocks) {
        Path file = dataFolder.resolve("panel.properties");
        if (!Files.isRegularFile(file)) return Optional.empty();
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file)) {
            p.load(r);
        } catch (IOException e) {
            return Optional.empty();
        }
        Map<String, String> saved = new HashMap<>();
        p.stringPropertyNames().forEach(k -> saved.put(k, p.getProperty(k)));
        try {
            Files.move(file, file.resolveSibling("panel.properties.migrated"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // read again next time; the values are the same
        }
        return Optional.of(OptionValues.fromStrings(BuildOptions.OPTIONS, saved, blocks));
    }
}
