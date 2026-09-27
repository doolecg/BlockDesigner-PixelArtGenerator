package io.blockdesigner.pixelart.palette;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.place.BlockPlacement.Dir;
import io.blockdesigner.pixelart.pipeline.OkLab;
import io.blockdesigner.plugin.AssetAccess;
import io.blockdesigner.plugin.BlockCatalog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How blocks look, for matching: the average colour of the top face and of the side faces, and how busy the texture
 * is. Worked out from the texture atlas of the loaded Minecraft version (so resource packs count), with the catalog's
 * average colour as the fallback while no game assets are loaded. Cached; safe to use from any thread.
 */
public final class BlockColours {

    /**
     * @param top        OKLab average of the top face
     * @param side       OKLab average of the four side faces
     * @param noise      spread of lightness over the texture (0 for a flat colour, ~0.1 for cobblestone)
     * @param seeThrough the faces are partly transparent (glass, leaves): not usable for pictures
     */
    public record Look(float[] top, float[] side, float noise, boolean seeThrough) {
        public float[] face(boolean topFace) {
            return topFace ? top : side;
        }
    }

    /** Where looks come from; a fake one stands in for tests. */
    @FunctionalInterface
    public interface Source {
        Look look(BlockState block);
    }

    private final AssetAccess assets;
    private final BlockCatalog catalog;
    private final Map<BlockState, Look> cache = new ConcurrentHashMap<>();
    private volatile Object atlasSeen;

    public BlockColours(AssetAccess assets, BlockCatalog catalog) {
        this.assets = assets;
        this.catalog = catalog;
    }

    public Source source() {
        return this::look;
    }

    public Look look(BlockState block) {
        // A newly loaded game version or resource pack brings a new atlas: forget the old looks.
        Object atlas = assets.available() ? assets.atlas().orElse(null) : null;
        if (atlas != atlasSeen) {
            cache.clear();
            atlasSeen = atlas;
        }
        return cache.computeIfAbsent(block, this::compute);
    }

    private Look compute(BlockState block) {
        AssetAccess.Atlas atlas = assets.available() ? assets.atlas().orElse(null) : null;
        if (atlas != null) {
            Stats top = new Stats(), side = new Stats();
            for (AssetAccess.Quad q : assets.quads(block, d -> false)) {
                if (q.face() == Dir.UP) sample(atlas, q, top);
                else if (q.face() != Dir.DOWN) sample(atlas, q, side);
            }
            if (top.n > 0 || side.n > 0) {
                if (top.n == 0) top = side;
                if (side.n == 0) side = top;
                float noise = (float) ((top.noise() + side.noise()) / 2);
                boolean seeThrough = top.clearFraction() > 0.1 || side.clearFraction() > 0.1;
                return new Look(top.mean(), side.mean(), noise, seeThrough);
            }
        }
        float[] c = OkLab.fromArgb(catalog.averageColor(block));
        return new Look(c, c, 0, false);
    }

    /** Adds every atlas pixel under the quad's uv rectangle, tinted like the game tints it. */
    private static void sample(AssetAccess.Atlas atlas, AssetAccess.Quad q, Stats into) {
        float u0 = 1, v0 = 1, u1 = 0, v1 = 0;
        for (int i = 0; i < 4; i++) {
            u0 = Math.min(u0, q.uvs()[2 * i]);
            u1 = Math.max(u1, q.uvs()[2 * i]);
            v0 = Math.min(v0, q.uvs()[2 * i + 1]);
            v1 = Math.max(v1, q.uvs()[2 * i + 1]);
        }
        int x0 = (int) Math.floor(u0 * atlas.width() + 1e-3), x1 = (int) Math.ceil(u1 * atlas.width() - 1e-3);
        int y0 = (int) Math.floor(v0 * atlas.height() + 1e-3), y1 = (int) Math.ceil(v1 * atlas.height() - 1e-3);
        x0 = Math.clamp(x0, 0, atlas.width() - 1);
        y0 = Math.clamp(y0, 0, atlas.height() - 1);
        x1 = Math.clamp(x1, x0 + 1, atlas.width());
        y1 = Math.clamp(y1, y0 + 1, atlas.height());
        int tint = q.tint();
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                int c = atlas.argb()[y * atlas.width() + x];
                if ((c >>> 24) < 128) {
                    into.clear++;
                    continue;
                }
                int r = (c >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
                int g = (c >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
                int b = (c & 0xFF) * (tint & 0xFF) / 255;
                into.add(r, g, b);
            }
    }

    /** Running sums over texture pixels. */
    private static final class Stats {
        double r, g, b, l, l2;
        int n, clear;

        void add(int r8, int g8, int b8) {
            float lr = OkLab.linear(r8), lg = OkLab.linear(g8), lb = OkLab.linear(b8);
            r += lr;
            g += lg;
            b += lb;
            float lightness = OkLab.fromLinear(lr, lg, lb)[0];
            l += lightness;
            l2 += lightness * lightness;
            n++;
        }

        float[] mean() {
            return OkLab.fromLinear(r / n, g / n, b / n);
        }

        double noise() {
            if (n == 0) return 0;
            double m = l / n;
            return Math.sqrt(Math.max(0, l2 / n - m * m));
        }

        double clearFraction() {
            return n + clear == 0 ? 0 : (double) clear / (n + clear);
        }
    }
}
