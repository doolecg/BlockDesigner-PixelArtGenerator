package io.blockdesigner.pixelart.pipeline;

/**
 * Gets a picture ready for building: trims a transparent border, scales it to the build's size in blocks and applies
 * the brightness, contrast and saturation settings.
 *
 * <p>Scaling averages the area each block covers (in linear light, weighted by alpha), so thin lines and fine detail
 * blend into the block instead of flickering in and out the way nearest-pixel sampling would.
 */
public final class Prepare {

    /**
     * @param width      target width in blocks; the height follows the aspect ratio
     * @param maxHeight  the height is capped here (the width shrinks to keep the aspect ratio)
     * @param trim       cut away fully transparent rows and columns around the picture first
     * @param brightness −1 … 1, 0 leaves it
     * @param contrast   0 … 2, 1 leaves it
     * @param saturation 0 … 2, 1 leaves it
     */
    public record Settings(int width, int maxHeight, boolean trim, double brightness, double contrast, double saturation) {
    }

    private Prepare() {
    }

    public static LabImage run(Picture src, Settings s) {
        Picture p = s.trim() ? trim(src) : src;
        int w = Math.max(1, s.width());
        int h = Math.max(1, (int) Math.round((double) w * p.height() / p.width()));
        if (h > s.maxHeight()) {
            h = s.maxHeight();
            w = Math.max(1, (int) Math.round((double) h * p.width() / p.height()));
        }
        LabImage img = resample(p, w, h);
        adjust(img, s.brightness(), s.contrast(), s.saturation());
        return img;
    }

    /** The picture without its fully transparent border; unchanged when nothing is transparent (or everything is). */
    public static Picture trim(Picture p) {
        int x0 = p.width(), y0 = p.height(), x1 = -1, y1 = -1;
        for (int y = 0; y < p.height(); y++)
            for (int x = 0; x < p.width(); x++)
                if (p.alpha(x, y) != 0) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
        if (x1 < 0 || (x0 == 0 && y0 == 0 && x1 == p.width() - 1 && y1 == p.height() - 1)) return p;
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) System.arraycopy(p.argb(), (y + y0) * p.width() + x0, out, y * w, w);
        return new Picture(w, h, out);
    }

    /** Area-average scaling to {@code w × h}, in premultiplied linear light. */
    static LabImage resample(Picture p, int w, int h) {
        int sw = p.width(), sh = p.height();
        // Premultiplied linear channels: r, g, b, a.
        float[] src = new float[sw * sh * 4];
        for (int i = 0; i < sw * sh; i++) {
            int c = p.argb()[i];
            float a = (c >>> 24) / 255f;
            src[4 * i] = OkLab.linear(c >> 16) * a;
            src[4 * i + 1] = OkLab.linear(c >> 8) * a;
            src[4 * i + 2] = OkLab.linear(c) * a;
            src[4 * i + 3] = a;
        }
        float[] rows = new float[w * sh * 4];
        Span[] xs = spans(sw, w);
        for (int y = 0; y < sh; y++)
            for (int x = 0; x < w; x++) accumulate(src, (y * sw) * 4, 4, xs[x], rows, (y * w + x) * 4);
        float[] out = new float[w * h * 4];
        Span[] ys = spans(sh, h);
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++) accumulate(rows, x * 4, w * 4, ys[y], out, (y * w + x) * 4);

        float[] lab = new float[w * h * 3];
        int[] alpha = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            float a = out[4 * i + 3];
            alpha[i] = Math.round(Math.clamp(a, 0f, 1f) * 255);
            float[] c = a > 1e-6f ? OkLab.fromLinear(out[4 * i] / a, out[4 * i + 1] / a, out[4 * i + 2] / a) : new float[3];
            System.arraycopy(c, 0, lab, 3 * i, 3);
        }
        return new LabImage(w, h, lab, alpha);
    }

    /** The source cells one target cell covers, with how much of each. */
    private record Span(int first, float[] weights) {
    }

    private static Span[] spans(int srcLen, int dstLen) {
        Span[] out = new Span[dstLen];
        double scale = (double) srcLen / dstLen;
        for (int i = 0; i < dstLen; i++) {
            double s0 = i * scale, s1 = (i + 1) * scale;
            int first = (int) Math.floor(s0), last = Math.min(srcLen - 1, (int) Math.ceil(s1) - 1);
            float[] wts = new float[last - first + 1];
            double total = 0;
            for (int j = first; j <= last; j++) total += wts[j - first] = (float) (Math.min(s1, j + 1) - Math.max(s0, j));
            for (int k = 0; k < wts.length; k++) wts[k] /= (float) total;
            out[i] = new Span(first, wts);
        }
        return out;
    }

    private static void accumulate(float[] src, int base, int stride, Span span, float[] dst, int at) {
        for (int k = 0; k < span.weights.length; k++) {
            int o = base + (span.first + k) * stride;
            float wt = span.weights[k];
            for (int c = 0; c < 4; c++) dst[at + c] += src[o + c] * wt;
        }
    }

    /** Brightness and contrast on L (around mid grey), saturation on a and b. */
    static void adjust(LabImage img, double brightness, double contrast, double saturation) {
        if (brightness == 0 && contrast == 1 && saturation == 1) return;
        float[] lab = img.lab();
        for (int i = 0; i < img.size(); i++) {
            lab[3 * i] = (float) Math.clamp((lab[3 * i] - 0.5) * contrast + 0.5 + brightness * 0.5, 0, 1);
            lab[3 * i + 1] *= (float) saturation;
            lab[3 * i + 2] *= (float) saturation;
        }
    }
}
