package io.blockdesigner.pixelart;

import io.blockdesigner.pixelart.palette.BlockColours;
import io.blockdesigner.pixelart.ui.CurrentBuild;
import io.blockdesigner.pixelart.ui.PixelArtPanel;
import io.blockdesigner.pixelart.ui.ImageImporter;
import io.blockdesigner.pixelart.ui.PlacePixelArtTool;
import io.blockdesigner.plugin.BlockDesignerPlugin;
import io.blockdesigner.plugin.PluginContext;

/**
 * Pixel Art Generator: turns a picture into blocks. The picture is split into colour regions (no AI), each region or pixel
 * is matched to the block that looks closest, and the result is built as a new layer. A Generate page in the plugin's
 * tab, a Place pixel art tool, and File › Import for pictures (sharing the page's settings).
 */
public final class PixelArtGeneratorPlugin implements BlockDesignerPlugin {
    @Override
    public void enable(PluginContext ctx) {
        BlockColours colours = new BlockColours(ctx.assets(), ctx.blocks());
        CurrentBuild current = new CurrentBuild();
        ctx.registerPanel(new PixelArtPanel(ctx, colours, current));
        ctx.registerImporter(new ImageImporter(colours));
        ctx.registerTool(new PlacePixelArtTool(ctx, current));
    }
}
