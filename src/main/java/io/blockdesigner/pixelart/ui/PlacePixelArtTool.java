package io.blockdesigner.pixelart.ui;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.pixelart.shape.Rotate;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;
import io.blockdesigner.plugin.PluginContext;
import io.blockdesigner.plugin.PluginTool;
import io.blockdesigner.plugin.ToolContext;
import io.blockdesigner.plugin.ToolEvent;
import io.blockdesigner.plugin.ToolHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Places the Pixel Art Generator page's result where the mouse points: shown as ghosts first, centred on the block face
 * under the mouse, placed with a click (one undo step). Shift+wheel or R turns it.
 */
public final class PlacePixelArtTool implements PluginTool {
    public static final String ID = "place-pixel-art";
    /** Bigger builds show only their outline while moving, so the view stays smooth. */
    private static final int MAX_GHOSTS = 60_000;
    private static final List<String> TURNS = List.of("0°", "90°", "180°", "270°");

    private final PluginContext ctx;
    private final CurrentBuild current;

    public PlacePixelArtTool(PluginContext ctx, CurrentBuild current) {
        this.ctx = ctx;
        this.current = current;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Place pixel art";
    }

    @Override
    public String description() {
        return "Click to place the Pixel Art Generator's result · Shift+wheel or R turns it";
    }

    @Override
    public String icon() {
        return "M2 2 H9 V7 H2 Z M2 6 L4.5 4 L6 5 L9 3.5 M8 9 H14 V15 H8 Z M11 7 V9";
    }

    @Override
    public Options options() {
        return Options.builder()
                .choice("turn", "Turn", TURNS, TURNS.getFirst())
                .toggle("keep", "Keep blocks already there", false)
                .build();
    }

    @Override
    public ToolHandler activate(ToolContext tc) {
        return new Handler(tc);
    }

    private final class Handler implements ToolHandler {
        private final ToolContext tc;
        private Structure turnedFrom;
        private int turnedBy = -1;
        private Structure turned;
        private Optional<BlockPos> lastAt = Optional.empty();

        Handler(ToolContext tc) {
            this.tc = tc;
            current.onChange(() -> show(lastAt));
            if (current.structure() == null) ctx.status("Open a picture in the Pixel Art Generator page first");
        }

        private int quarters() {
            return Math.max(0, TURNS.indexOf(tc.options().choice("turn")));
        }

        /** The build turned as the options say, cached. */
        private Structure build() {
            Structure s = current.structure();
            if (s == null || s.isEmpty()) return null;
            if (s != turnedFrom || quarters() != turnedBy) {
                turned = Rotate.quarters(s, quarters());
                turnedFrom = s;
                turnedBy = quarters();
            }
            return turned;
        }

        private Optional<BlockPos> anchor(ToolEvent e) {
            return e.hit().map(ToolEvent.Hit::adjacent);
        }

        private void show(Optional<BlockPos> at) {
            lastAt = at;
            Structure s = build();
            if (s == null || at.isEmpty()) {
                tc.preview().clear();
                return;
            }
            BlockPos off = Rotate.offsetCentredOn(s, at.get());
            Box b = s.bounds().orElseThrow();
            tc.preview().outline(new Box(b.minX() + off.x(), b.minY() + off.y(), b.minZ() + off.z(),
                    b.maxX() + off.x(), b.maxY() + off.y(), b.maxZ() + off.z()));
            if (s.blockCount() > MAX_GHOSTS) {
                tc.preview().ghost(Map.of());
                return;
            }
            Map<BlockPos, BlockState> ghosts = new HashMap<>();
            s.forEachBlock((x, y, z, st) -> ghosts.put(new BlockPos(x + off.x(), y + off.y(), z + off.z()), st));
            tc.preview().ghost(ghosts);
        }

        @Override
        public void hover(ToolEvent e) {
            Optional<BlockPos> at = anchor(e);
            if (!at.equals(lastAt)) show(at);
        }

        @Override
        public void press(ToolEvent e) {
            if (e.button() != ToolEvent.Button.PRIMARY) return;
            Structure s = build();
            Optional<BlockPos> at = anchor(e);
            if (s == null) {
                ctx.toast("Open a picture in the Pixel Art Generator page first");
                return;
            }
            if (at.isEmpty()) return;
            BlockPos off = Rotate.offsetCentredOn(s, at.get());
            boolean keep = tc.options().toggle("keep");
            try (ToolContext.Stroke stroke = tc.beginStroke("Place " + current.name())) {
                s.forEachBlock((x, y, z, st) -> {
                    BlockPos p = new BlockPos(x + off.x(), y + off.y(), z + off.z());
                    if (keep && !stroke.world().get(p).isAir()) return;
                    stroke.world().set(p, st);
                });
            }
            ctx.status("Placed " + current.name());
        }

        @Override
        public boolean scroll(ToolEvent e, double delta) {
            if (!e.shift()) return false;
            turn(delta > 0 ? 1 : -1);
            return true;
        }

        @Override
        public boolean key(String key) {
            if (key.equals("R")) {
                turn(1);
                return true;
            }
            if (key.equals("Shift+R")) {
                turn(-1);
                return true;
            }
            return false;
        }

        private void turn(int by) {
            String next = TURNS.get(Math.floorMod(quarters() + by, 4));
            ctx.setToolOptions(ID, (OptionValues v) -> v.with("turn", next));
        }

        @Override
        public void optionsChanged() {
            show(lastAt);
        }

        @Override
        public void deactivate() {
            current.onChange(null);
            tc.preview().clear();
        }
    }
}
