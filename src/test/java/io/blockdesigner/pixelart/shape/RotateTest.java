package io.blockdesigner.pixelart.shape;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RotateTest {

    @Test
    void quarterTurnIsClockwiseFromAbove() {
        Structure s = new Structure();
        s.set(0, 0, 0, BlockState.of("stone"));
        s.set(2, 0, 0, BlockState.of("oak_stairs", java.util.Map.of("facing", "east", "half", "bottom", "shape", "straight")));
        Structure t = Rotate.quarters(s, 1);
        // What was east of the other block is now south of it, and its stairs face south.
        assertThat(t.get(0, 0, 0)).isEqualTo(BlockState.of("stone"));
        assertThat(t.get(0, 0, 2).get("facing")).isEqualTo("south");
        assertThat(t.bounds().orElseThrow().min()).isEqualTo(BlockPos.ORIGIN);
    }

    @Test
    void fourTurnsGetBackToTheStart() {
        Structure s = new Structure();
        s.set(0, 0, 0, BlockState.of("stone"));
        s.set(3, 1, 1, BlockState.of("oak_stairs", java.util.Map.of("facing", "north", "half", "top", "shape", "inner_left")));
        s.set(1, 0, 2, BlockState.of("oak_log", java.util.Map.of("axis", "x")));
        assertThat(Rotate.quarters(Rotate.quarters(s, 3), 1).contentEquals(s)).isTrue();
        assertThat(Rotate.state(BlockState.of("oak_log", java.util.Map.of("axis", "x")), 1).get("axis")).isEqualTo("z");
    }

    @Test
    void centredOnTheTarget() {
        Structure s = new Structure();
        for (int x = 0; x < 5; x++) s.set(x, 0, 0, BlockState.of("stone"));
        BlockPos off = Rotate.offsetCentredOn(s, new BlockPos(10, 64, 10));
        assertThat(off).isEqualTo(new BlockPos(8, 64, 10));
    }
}
