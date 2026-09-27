package io.blockdesigner.pixelart.shape;

import io.blockdesigner.core.model.BlockPos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlatShapeTest {

    @Test
    void wallsReadTheRightWayRoundFromTheirSide() {
        // The picture's top-left pixel of a 4×3 picture.
        assertThat(Orientation.SOUTH.place(0, 0, 0, 4, 3, 1)).isEqualTo(new BlockPos(0, 2, 0));
        assertThat(Orientation.NORTH.place(0, 0, 0, 4, 3, 1)).isEqualTo(new BlockPos(3, 2, 0));
        assertThat(Orientation.EAST.place(0, 0, 0, 4, 3, 1)).isEqualTo(new BlockPos(0, 2, 3));
        assertThat(Orientation.WEST.place(0, 0, 0, 4, 3, 1)).isEqualTo(new BlockPos(0, 2, 0));
    }

    @Test
    void floorsHaveNorthAtTheTop() {
        assertThat(Orientation.FLOOR.place(0, 0, 0, 4, 3, 1)).isEqualTo(new BlockPos(0, 0, 0));
        assertThat(Orientation.FLOOR.place(3, 2, 0, 4, 3, 1)).isEqualTo(new BlockPos(3, 0, 2));
    }

    @Test
    void depthComesTowardsTheViewer() {
        assertThat(Orientation.SOUTH.place(0, 0, 2, 4, 3, 3).z()).isEqualTo(2);
        assertThat(Orientation.NORTH.place(0, 0, 2, 4, 3, 3).z()).isEqualTo(0);
        assertThat(Orientation.EAST.place(0, 0, 2, 4, 3, 3).x()).isEqualTo(2);
        assertThat(Orientation.WEST.place(0, 0, 2, 4, 3, 3).x()).isEqualTo(0);
    }
}
