package io.blockdesigner.pixelart.pipeline;

/**
 * Colours in OKLab (Björn Ottosson, 2020): a perceptual space where straight-line distance is close to how different
 * two colours look, so nearest-colour matching and averaging behave the way the eye expects. A colour is a
 * {@code float[3]} of L (0 black … 1 white), a and b (roughly −0.4 … 0.4).
 */
public final class OkLab {
    private static final float[] TO_LINEAR = new float[256];

    static {
        for (int i = 0; i < 256; i++) TO_LINEAR[i] = (float) srgbToLinear(i / 255.0);
    }

    private OkLab() {
    }

    /** One sRGB channel (0..1) to linear light. */
    public static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** One linear-light channel to sRGB (0..1), clamped. */
    public static double linearToSrgb(double c) {
        c = Math.clamp(c, 0.0, 1.0);
        return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
    }

    /** A byte channel (0..255) to linear light, from a table. */
    public static float linear(int channel) {
        return TO_LINEAR[channel & 0xFF];
    }

    /** {@code 0xAARRGGBB} (alpha ignored) to OKLab. */
    public static float[] fromArgb(int argb) {
        return fromLinear(linear(argb >> 16), linear(argb >> 8), linear(argb));
    }

    /** Linear-light RGB (0..1) to OKLab. */
    public static float[] fromLinear(double r, double g, double b) {
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new float[]{
                (float) (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s),
                (float) (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s),
                (float) (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)};
    }

    /** OKLab to linear-light RGB, unclamped (colours outside sRGB come out below 0 or above 1). */
    public static double[] toLinear(float[] lab) {
        double l = lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2];
        double m = lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2];
        double s = lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2];
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        return new double[]{
                4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s};
    }

    /** OKLab to opaque {@code 0xFFRRGGBB}, clamped into sRGB. */
    public static int toArgb(float[] lab) {
        double[] rgb = toLinear(lab);
        int r = (int) Math.round(linearToSrgb(rgb[0]) * 255);
        int g = (int) Math.round(linearToSrgb(rgb[1]) * 255);
        int b = (int) Math.round(linearToSrgb(rgb[2]) * 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Squared distance between two OKLab colours. */
    public static float dist2(float[] p, float[] q) {
        float dl = p[0] - q[0], da = p[1] - q[1], db = p[2] - q[2];
        return dl * dl + da * da + db * db;
    }

    /** Squared distance with a and b given as separate floats (for hot loops over flat arrays). */
    public static float dist2(float[] p, float l, float a, float b) {
        float dl = p[0] - l, da = p[1] - a, db = p[2] - b;
        return dl * dl + da * da + db * db;
    }
}
