package io.blockdesigner.pixelart.helpers;

import io.blockdesigner.pixelart.pipeline.Picture;

/**
 * Cuts the subject out of a photo. A hook for a local background-removal model; until one is installed the builder
 * uses transparency or the border colour ({@link io.blockdesigner.pixelart.pipeline.Background}).
 */
public interface Segmenter {

    boolean ready();

    /** @return {@code width × height} flags, row by row: true for the subject */
    boolean[] subject(Picture picture, int width, int height) throws Exception;
}
