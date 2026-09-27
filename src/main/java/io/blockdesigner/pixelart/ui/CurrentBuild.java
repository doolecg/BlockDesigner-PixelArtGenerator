package io.blockdesigner.pixelart.ui;

import io.blockdesigner.core.model.Structure;

/** The panel's latest result, for the Place pixel art tool. Set and read on the JavaFX thread. */
public final class CurrentBuild {
    private Structure structure;
    private String name;
    private Runnable listener = () -> {
    };

    public CurrentBuild() {
    }

    void set(Structure structure, String name) {
        this.structure = structure;
        this.name = name;
        listener.run();
    }

    Structure structure() {
        return structure;
    }

    String name() {
        return name;
    }

    /** Called after every change (the tool refreshes its ghost). */
    void onChange(Runnable listener) {
        this.listener = listener == null ? () -> {
        } : listener;
    }
}
