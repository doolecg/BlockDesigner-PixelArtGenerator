package io.blockdesigner.pixelart.helpers;

import io.blockdesigner.pixelart.pipeline.Picture;

/**
 * Guesses how far each pixel of a photo is from the camera. A hook for a local depth model (planned: Depth Anything
 * V2 Small through ONNX Runtime); until one is installed the builder uses brightness or a depth image instead.
 */
public interface DepthEstimator {

    /** Whether it can run now (model downloaded, runtime loaded). */
    boolean ready();

    /**
     * @return {@code width × height} values, row by row, 0 for the farthest point and 1 for the nearest
     */
    float[] depth(Picture picture, int width, int height) throws Exception;
}
