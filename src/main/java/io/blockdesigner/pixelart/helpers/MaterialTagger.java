package io.blockdesigner.pixelart.helpers;

import io.blockdesigner.pixelart.pipeline.Picture;
import io.blockdesigner.pixelart.pipeline.Segmentation;

import java.util.Map;

/**
 * Names what each region of a picture shows ("brick", "leaves", "water", "glass"), so blocks can be picked by meaning
 * as well as colour. A hook for a local vision model (planned: a small model served by Ollama); off by default.
 */
public interface MaterialTagger {

    boolean ready();

    /** @return a word per region index; regions it can't name are left out */
    Map<Integer, String> tags(Picture picture, Segmentation regions) throws Exception;
}
