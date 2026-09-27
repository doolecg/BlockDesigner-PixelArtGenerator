package io.blockdesigner.pixelart.pipeline;

/**
 * An image in OKLab at block resolution: one pixel per block column of the build.
 *
 * @param lab   3 floats per pixel (L, a, b), row by row from the top left
 * @param alpha 0..255 per pixel
 */
public record LabImage(int width, int height, float[] lab, int[] alpha) {

    public int size() {
        return width * height;
    }

    public float[] lab(int i) {
        return new float[]{lab[3 * i], lab[3 * i + 1], lab[3 * i + 2]};
    }

    /** For previews: the colours back as opaque sRGB, fully transparent where alpha is 0. */
    public int[] toArgb() {
        int[] out = new int[size()];
        float[] c = new float[3];
        for (int i = 0; i < out.length; i++) {
            c[0] = lab[3 * i];
            c[1] = lab[3 * i + 1];
            c[2] = lab[3 * i + 2];
            out[i] = (OkLab.toArgb(c) & 0xFFFFFF) | alpha[i] << 24;
        }
        return out;
    }
}
