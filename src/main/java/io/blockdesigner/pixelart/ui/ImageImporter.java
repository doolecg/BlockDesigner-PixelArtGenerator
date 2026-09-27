package io.blockdesigner.pixelart.ui;

import io.blockdesigner.core.model.Structure;
import io.blockdesigner.pixelart.BuildOptions;
import io.blockdesigner.pixelart.palette.BlockColours;
import io.blockdesigner.pixelart.palette.CatalogFamilies;
import io.blockdesigner.pixelart.pipeline.Picture;
import io.blockdesigner.pixelart.pipeline.Pipeline;
import io.blockdesigner.plugin.BlockCatalog;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;
import io.blockdesigner.plugin.PluginImporter;
import io.blockdesigner.plugin.Progress;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** File › Import on a picture: builds it with the options the Import dialog asks for, no panel needed. */
public final class ImageImporter implements PluginImporter {
    private final BlockColours colours;

    public ImageImporter(BlockColours colours) {
        this.colours = colours;
    }

    @Override
    public String id() {
        return "image";
    }

    @Override
    public String displayName() {
        return "Pixel art from a picture (Pixel Art Generator)";
    }

    @Override
    public List<String> extensions() {
        return List.of("png", "jpg", "jpeg", "bmp", "gif");
    }

    @Override
    public Options options() {
        return BuildOptions.OPTIONS;
    }

    @Override
    public List<ImportedLayer> importFile(Path file, OptionValues options, Progress progress, BlockCatalog blocks) throws IOException {
        progress.update(0.1, "Reading " + file.getFileName());
        Picture pic = Picture.read(file);
        progress.update(0.3, "Matching blocks");
        Pipeline.Result r;
        try {
            r = new Pipeline(colours.source(), blocks::exists, blocks::resolve, CatalogFamilies.of(blocks)).run(pic, BuildOptions.settings(options));
        } catch (IllegalArgumentException | java.io.UncheckedIOException e) {
            throw new IOException(e.getMessage(), e);
        }
        Structure s = r.structure();
        progress.update(1, null);
        return s.isEmpty() ? List.of() : List.of(new ImportedLayer("Pixel art: " + file.getFileName(), s, null));
    }
}
