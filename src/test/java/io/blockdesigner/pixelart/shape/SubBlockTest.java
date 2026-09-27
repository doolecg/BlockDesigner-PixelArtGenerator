package io.blockdesigner.pixelart.shape;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubBlockTest {

    /**
     * Octants each oak_stairs state fills, read from Minecraft 26.3's own blockstate and models (a script that
     * rotates the model elements the way the game does).
     */
    private static final Map<String, Integer> GAME = Map.ofEntries(
            Map.entry("east,bottom,inner_left", 191), Map.entry("east,bottom,inner_right", 239),
            Map.entry("east,bottom,outer_left", 47), Map.entry("east,bottom,outer_right", 143),
            Map.entry("east,bottom,straight", 175), Map.entry("east,top,inner_left", 251),
            Map.entry("east,top,inner_right", 254), Map.entry("east,top,outer_left", 242),
            Map.entry("east,top,outer_right", 248), Map.entry("east,top,straight", 250),
            Map.entry("north,bottom,inner_left", 127), Map.entry("north,bottom,inner_right", 191),
            Map.entry("north,bottom,outer_left", 31), Map.entry("north,bottom,outer_right", 47),
            Map.entry("north,bottom,straight", 63), Map.entry("north,top,inner_left", 247),
            Map.entry("north,top,inner_right", 251), Map.entry("north,top,outer_left", 241),
            Map.entry("north,top,outer_right", 242), Map.entry("north,top,straight", 243),
            Map.entry("south,bottom,inner_left", 239), Map.entry("south,bottom,inner_right", 223),
            Map.entry("south,bottom,outer_left", 143), Map.entry("south,bottom,outer_right", 79),
            Map.entry("south,bottom,straight", 207), Map.entry("south,top,inner_left", 254),
            Map.entry("south,top,inner_right", 253), Map.entry("south,top,outer_left", 248),
            Map.entry("south,top,outer_right", 244), Map.entry("south,top,straight", 252),
            Map.entry("west,bottom,inner_left", 223), Map.entry("west,bottom,inner_right", 127),
            Map.entry("west,bottom,outer_left", 79), Map.entry("west,bottom,outer_right", 31),
            Map.entry("west,bottom,straight", 95), Map.entry("west,top,inner_left", 253),
            Map.entry("west,top,inner_right", 247), Map.entry("west,top,outer_left", 244),
            Map.entry("west,top,outer_right", 241), Map.entry("west,top,straight", 245));

    @Test
    void stairsMatchTheGame() {
        Map<String, Integer> ours = new HashMap<>();
        for (SubBlock.Shape s : SubBlock.allStairs())
            ours.put(s.properties().get("facing") + "," + s.properties().get("half") + "," + s.properties().get("shape"), s.mask());
        assertThat(ours).isEqualTo(GAME);
    }

    @Test
    void exactShapesComeBack() {
        for (SubBlock.Shape s : SubBlock.allStairs()) {
            SubBlock.Shape got = SubBlock.of(s.mask(), true, true);
            // Some masks are two stairs at once (north inner_right is east inner_left); either is the same block.
            assertThat(got.kind()).isEqualTo(SubBlock.Kind.STAIRS);
            assertThat(got.mask()).isEqualTo(s.mask());
        }
        assertThat(SubBlock.of(0x0F, true, true).properties()).containsEntry("type", "bottom");
        assertThat(SubBlock.of(0xF0, true, true).properties()).containsEntry("type", "top");
        assertThat(SubBlock.of(0xFF, true, true)).isEqualTo(SubBlock.FULL);
        assertThat(SubBlock.of(0, true, true)).isEqualTo(SubBlock.AIR);
    }

    @Test
    void nearMissesTakeTheClosestShape() {
        // A bottom slab with one octant on top: an outer corner stair.
        SubBlock.Shape s = SubBlock.of(0x0F | SubBlock.bit(0, 1, 0), true, true);
        assertThat(s.kind()).isEqualTo(SubBlock.Kind.STAIRS);
        assertThat(s.properties()).containsEntry("shape", "outer_left").containsEntry("facing", "north");
        // Without stairs the same becomes a slab (one octant off) rather than a full block (three off).
        assertThat(SubBlock.of(0x0F | SubBlock.bit(0, 1, 0), true, false).properties()).containsEntry("type", "bottom");
        // One lone octant: air, not a block four times its size.
        assertThat(SubBlock.of(SubBlock.bit(1, 0, 1), true, true)).isEqualTo(SubBlock.AIR);
    }

    @Test
    void withoutSlabsOrStairsOnlyFullAndAir() {
        for (int m = 0; m < 256; m++) {
            SubBlock.Kind k = SubBlock.of(m, false, false).kind();
            assertThat(k).isIn(SubBlock.Kind.FULL, SubBlock.Kind.AIR);
            // Half full or more stays a block.
            if (Integer.bitCount(m) > 4) assertThat(k).isEqualTo(SubBlock.Kind.FULL);
            if (Integer.bitCount(m) < 4) assertThat(k).isEqualTo(SubBlock.Kind.AIR);
        }
    }
}
