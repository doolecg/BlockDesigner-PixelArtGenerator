package io.blockdesigner.pixelart.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Settings the page kept itself before 0.2.0 move to BlockDesigner (shared with the importer) once. */
class PanelMigrationTest {
    @TempDir
    Path dir;

    @Test
    void oldPanelSettingsAreReadOnceAndTheFileSetAside() throws Exception {
        Files.writeString(dir.resolve("panel.properties"), "#Pixel Art Generator panel settings\nwidth=96\nmode=Relief / heightmap\ntrim=false\n");
        var v = OldPanelSettings.migrate(dir, null);
        assertThat(v).isPresent();
        assertThat(v.get().integer("width")).isEqualTo(96);
        assertThat(v.get().choice("mode")).isEqualTo("Relief / heightmap");
        assertThat(v.get().toggle("trim")).isFalse();
        assertThat(dir.resolve("panel.properties")).doesNotExist();
        assertThat(dir.resolve("panel.properties.migrated")).exists();
        assertThat(OldPanelSettings.migrate(dir, null)).as("only once").isEmpty();
    }

    @Test
    void nothingToMigrate() {
        assertThat(OldPanelSettings.migrate(dir, null)).isEmpty();
    }
}
