package io.blockdesigner.pixelart.shape;

/** What kind of build a picture becomes. */
public enum BuildMode {
    FLAT("Flat pixel art"),
    RELIEF("Relief / heightmap"),
    EXTRUDE("Extruded silhouette"),
    INFLATE("3D: inflate (rounded)"),
    REVOLVE("3D: revolve (lathe)");

    public final String label;

    BuildMode(String label) {
        this.label = label;
    }

    /** Whether the build has depth (so half-block detail and hollowing apply). */
    public boolean solid() {
        return this != FLAT;
    }

    /** The 3D guesses and extrusions grow with the picture's size in every direction, so they are capped smaller. */
    public int maxSize() {
        return this == FLAT || this == RELIEF ? 512 : 256;
    }

    public static BuildMode of(String label) {
        for (BuildMode m : values()) if (m.label.equals(label)) return m;
        return FLAT;
    }
}
