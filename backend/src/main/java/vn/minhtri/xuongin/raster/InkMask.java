package vn.minhtri.xuongin.raster;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Whole-sheet "has ink" mask, 1 byte per pixel, row-major {@code width × height}:
 * {@link #INK} = 1, {@link #NONE} = 0 (spec 10.3). The only whole-sheet array kept while
 * rendering in bands (spec 10.6); shared by the TIF (W1 = choked mask, layer alpha = raw
 * mask) and the cut file (dilated raw mask), so both read the same definition.
 *
 * <p>Usage: feed every band through {@link #accept} (it is a {@link BandConsumer}), then call
 * {@link #finish} once with the zones of pieces that come from opaque images.
 *
 * <p>Rule: alpha above the threshold is ink. Near-white pixels are kept as ink too, except
 * inside opaque-image zones (JPG, PNG without alpha) where near-white reachable from the zone
 * edge, the sheet edge or a transparent pixel is the photo's background and is dropped
 * (magic wand). PDFs and PNGs with alpha are never flooded: their transparency already says
 * what is not printed, so remaining white is deliberate artwork (white text must get ink).
 */
public final class InkMask implements BandConsumer {

    public static final byte NONE = 0;
    public static final byte INK = 1;

    /** Ink, but near white: background or white detail, decided by {@link #finish}. */
    static final byte PALE = 2;

    private final int width;
    private final int height;
    private final int alphaThreshold;
    private final int paleFloor;
    private final byte[] mask;
    private boolean finished;

    public InkMask(int width, int height, int alphaThreshold, int whiteTolerance) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("empty raster " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.alphaThreshold = alphaThreshold;
        this.paleFloor = 255 - whiteTolerance;
        this.mask = new byte[Math.multiplyExact(width, height)];
    }

    public static InkMask of(RasterSize size, RasterProperties properties) {
        return new InkMask(
                size.width(), size.height(), properties.whiteAlphaThreshold(), properties.whiteTolerance());
    }

    /**
     * Renders {@code sheetPdf} in bands and returns its finished mask.
     *
     * @param opaqueZones zones of pieces from opaque images, see {@code PdfComposer.opaqueImageZones}
     */
    public static byte[] build(
            SheetRasterizer rasterizer,
            Path sheetPdf,
            int dpi,
            RasterProperties properties,
            double widthMm,
            double lengthMm,
            List<PixelRect> opaqueZones)
            throws IOException {
        InkMask inkMask = of(SheetRasterizer.sizeOf(widthMm, lengthMm, dpi), properties);
        rasterizer.render(sheetPdf, dpi, inkMask);
        return inkMask.finish(opaqueZones);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Classifies one band. Per-pixel only, so band boundaries cannot change the result. */
    @Override
    public void accept(int y, int rows, int bandWidth, int[] argb) {
        if (bandWidth != width || y < 0 || y + rows > height || argb.length < rows * width) {
            throw new IllegalArgumentException(
                    "band " + y + "+" + rows + "x" + bandWidth + " does not fit mask " + width + "x" + height);
        }
        int offset = y * width;
        int count = rows * width;
        for (int i = 0; i < count; i++) {
            mask[offset + i] = classify(argb[i]);
        }
    }

    private byte classify(int argb) {
        if (argb >>> 24 <= alphaThreshold) {
            return NONE;
        }
        boolean pale = ((argb >> 16) & 0xFF) >= paleFloor
                && ((argb >> 8) & 0xFF) >= paleFloor
                && (argb & 0xFF) >= paleFloor;
        return pale ? PALE : INK;
    }

    /**
     * Drops the background of opaque images and settles every remaining near-white pixel as
     * ink. Must run after all bands: the flood crosses band boundaries.
     *
     * @param opaqueZones pixel zones of pieces from images without alpha; null or empty = none
     * @return the mask (the same array, not a copy)
     */
    public byte[] finish(List<PixelRect> opaqueZones) {
        if (finished) {
            throw new IllegalStateException("mask already finished");
        }
        finished = true;
        if (opaqueZones != null) {
            for (PixelRect zone : opaqueZones) {
                PixelRect clipped = zone.clip(width, height);
                if (!clipped.isEmpty()) {
                    floodZone(clipped);
                }
            }
        }
        for (int i = 0; i < mask.length; i++) {
            if (mask[i] == PALE) {
                mask[i] = INK;
            }
        }
        return mask;
    }

    private void floodZone(PixelRect zone) {
        int[] stack = new int[256];
        int top = 0;
        for (int y = zone.y0(); y < zone.y1(); y++) {
            for (int x = zone.x0(); x < zone.x1(); x++) {
                int i = y * width + x;
                if (mask[i] == PALE && isSeed(zone, x, y, i)) {
                    mask[i] = NONE;
                    stack = push(stack, top++, i);
                }
            }
        }
        while (top > 0) {
            int i = stack[--top];
            int x = i % width;
            int y = i / width;
            if (x > zone.x0() && mask[i - 1] == PALE) {
                mask[i - 1] = NONE;
                stack = push(stack, top++, i - 1);
            }
            if (x + 1 < zone.x1() && mask[i + 1] == PALE) {
                mask[i + 1] = NONE;
                stack = push(stack, top++, i + 1);
            }
            if (y > zone.y0() && mask[i - width] == PALE) {
                mask[i - width] = NONE;
                stack = push(stack, top++, i - width);
            }
            if (y + 1 < zone.y1() && mask[i + width] == PALE) {
                mask[i + width] = NONE;
                stack = push(stack, top++, i + width);
            }
        }
    }

    /** On the zone edge (which includes the sheet edge) or next to a transparent pixel. */
    private boolean isSeed(PixelRect zone, int x, int y, int i) {
        if (x == zone.x0() || y == zone.y0() || x == zone.x1() - 1 || y == zone.y1() - 1) {
            return true;
        }
        return mask[i - 1] == NONE || mask[i + 1] == NONE || mask[i - width] == NONE || mask[i + width] == NONE;
    }

    private static int[] push(int[] stack, int top, int value) {
        int[] target = top == stack.length ? Arrays.copyOf(stack, stack.length * 2) : stack;
        target[top] = value;
        return target;
    }

    /**
     * Shrinks the ink by {@code radius} pixels on every side (square kernel). Outside the sheet
     * counts as no ink, so artwork touching the edge shrinks too. Used for W1 (spec 10.3).
     */
    public static byte[] erode(byte[] mask, int width, int height, int radius) {
        return radius <= 0 ? mask.clone() : morph(mask, width, height, radius, true);
    }

    /** Grows the ink by {@code radius} pixels on every side (square kernel). Used for cut offset. */
    public static byte[] dilate(byte[] mask, int width, int height, int radius) {
        return radius <= 0 ? mask.clone() : morph(mask, width, height, radius, false);
    }

    /** Two 1-D sliding-window passes: same result as a square kernel, O(pixels). */
    private static byte[] morph(byte[] mask, int width, int height, int radius, boolean erode) {
        int window = 2 * radius + 1;
        byte[] pass = new byte[mask.length];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int count = 0;
            for (int x = 0; x < Math.min(radius, width); x++) {
                count += mask[row + x] != NONE ? 1 : 0;
            }
            for (int x = 0; x < width; x++) {
                int entering = x + radius;
                if (entering < width && mask[row + entering] != NONE) {
                    count++;
                }
                int leaving = x - radius - 1;
                if (leaving >= 0 && mask[row + leaving] != NONE) {
                    count--;
                }
                pass[row + x] = decide(count, window, erode);
            }
        }
        byte[] out = new byte[mask.length];
        for (int x = 0; x < width; x++) {
            int count = 0;
            for (int y = 0; y < Math.min(radius, height); y++) {
                count += pass[y * width + x] != NONE ? 1 : 0;
            }
            for (int y = 0; y < height; y++) {
                int entering = y + radius;
                if (entering < height && pass[entering * width + x] != NONE) {
                    count++;
                }
                int leaving = y - radius - 1;
                if (leaving >= 0 && pass[leaving * width + x] != NONE) {
                    count--;
                }
                out[y * width + x] = decide(count, window, erode);
            }
        }
        return out;
    }

    private static byte decide(int count, int window, boolean erode) {
        boolean ink = erode ? count == window : count > 0;
        return ink ? INK : NONE;
    }
}
