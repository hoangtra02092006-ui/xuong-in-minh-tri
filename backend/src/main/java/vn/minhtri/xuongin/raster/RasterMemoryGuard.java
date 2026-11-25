package vn.minhtri.xuongin.raster;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;

/**
 * Refuses a raster job before it starts when it would not fit in memory (spec 10.6): above
 * {@code heap × maxHeapFraction} or above {@code maxMegapixels} → {@code TIFF_TOO_LARGE} with a
 * suggested DPI (and a suggested max sheet length) in the details. A clear refusal beats the
 * OS killing the process and losing every in-memory job.
 *
 * <p>The estimate counts arrays alive at the same time, in bytes per pixel:
 * <ul>
 *   <li>whole sheet, {@value #SHEET_BYTES_PER_PIXEL}: raw mask 1 + choked W1 mask 1 + compressed
 *       band data and encoder output 1 (allowance, print sheets compress well);</li>
 *   <li>one band of {@code bandRows + 2 × overlap} rows, {@value #BAND_BYTES_PER_PIXEL}: rendered
 *       ARGB 4 + CMYK conversion 4 + 5-channel samples 5.</li>
 * </ul>
 * Off-heap memory of ImageIO / ICC / PDFBox is not counted, hence the fraction (default 0.5).
 */
@Component
public class RasterMemoryGuard {

    static final int SHEET_BYTES_PER_PIXEL = 3;
    static final int BAND_BYTES_PER_PIXEL = 13;
    private static final int MIN_SUGGESTED_DPI = 10;
    private static final int DPI_STEP = 10;
    private static final long MB = 1024L * 1024L;

    private final RasterProperties properties;
    private final LongSupplier maxHeap;
    private final int bandRows;

    @Autowired
    public RasterMemoryGuard(RasterProperties properties) {
        this(properties, Runtime.getRuntime()::maxMemory);
    }

    /** @param maxHeap max heap in bytes (injectable for tests) */
    public RasterMemoryGuard(RasterProperties properties, LongSupplier maxHeap) {
        this.properties = properties;
        this.maxHeap = maxHeap;
        this.bandRows = SheetRasterizer.roundBandRows(properties.bandRows());
    }

    /** Estimated peak heap bytes to rasterize one sheet of {@code widthPx × heightPx}. */
    public static long estimateBytes(int widthPx, int heightPx, int bandRows) {
        long sheet = (long) widthPx * heightPx * SHEET_BYTES_PER_PIXEL;
        long bandHeight = Math.min((long) bandRows + 2L * SheetRasterizer.OVERLAP, heightPx);
        return sheet + bandHeight * widthPx * BAND_BYTES_PER_PIXEL;
    }

    /** Same with the configured band height. */
    public long estimateBytes(int widthPx, int heightPx) {
        return estimateBytes(widthPx, heightPx, bandRows);
    }

    /** Bytes one sheet may use: {@code maxHeap × maxHeapFraction}. */
    public long allowedBytes() {
        return (long) (maxHeap.getAsLong() * properties.maxHeapFraction());
    }

    /**
     * Throws {@code TIFF_TOO_LARGE} when the sheet cannot be rasterized at {@code dpi}.
     *
     * @return the raster size when it fits
     */
    public RasterSize check(int sheetIndex, double widthMm, double lengthMm, int dpi) {
        RasterSize size = SheetRasterizer.sizeOf(widthMm, lengthMm, dpi);
        if (fits(size)) {
            return size;
        }
        Integer suggestedDpi = suggestDpi(widthMm, lengthMm, dpi);
        Double suggestedLengthMm = suggestLengthMm(widthMm, lengthMm, dpi);
        long needMb = estimateBytes(size.width(), size.height()) / MB;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sheetIndex", sheetIndex);
        details.put("dpi", dpi);
        details.put("widthPx", size.width());
        details.put("heightPx", size.height());
        details.put("megapixels", Math.round(size.pixels() / 1_000_000.0));
        details.put("maxMegapixels", properties.maxMegapixels());
        details.put("estimatedMb", needMb);
        details.put("allowedMb", allowedBytes() / MB);
        details.put("heapMaxMb", maxHeap.getAsLong() / MB);
        if (suggestedDpi != null) {
            details.put("suggestedDpi", suggestedDpi);
        }
        if (suggestedLengthMm != null) {
            details.put("suggestedMaxLengthMm", suggestedLengthMm);
        }
        throw new ApiException(ErrorCode.TIFF_TOO_LARGE, message(sheetIndex, widthMm, lengthMm, dpi,
                size, needMb, suggestedDpi, suggestedLengthMm), details);
    }

    boolean fits(RasterSize size) {
        return size.pixels() <= (long) properties.maxMegapixels() * 1_000_000L
                && estimateBytes(size.width(), size.height()) <= allowedBytes();
    }

    /** Highest multiple of 10 below {@code dpi} that fits; null when even 10 DPI does not. */
    Integer suggestDpi(double widthMm, double lengthMm, int dpi) {
        int candidate = (dpi - 1) / DPI_STEP * DPI_STEP;
        for (; candidate >= MIN_SUGGESTED_DPI; candidate -= DPI_STEP) {
            if (fits(SheetRasterizer.sizeOf(widthMm, lengthMm, candidate))) {
                return candidate;
            }
        }
        return null;
    }

    /** Longest sheet (whole cm, in mm) that fits at {@code dpi}; null when not even 1 cm does. */
    Double suggestLengthMm(double widthMm, double lengthMm, int dpi) {
        long lo = 0;
        long hi = (long) Math.floor(lengthMm / 10.0);
        while (lo < hi) {
            long mid = (lo + hi + 1) / 2;
            if (fits(SheetRasterizer.sizeOf(widthMm, mid * 10.0, dpi))) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo == 0 ? null : lo * 10.0;
    }

    private static String message(int sheetIndex, double widthMm, double lengthMm, int dpi, RasterSize size,
            long needMb, Integer suggestedDpi, Double suggestedLengthMm) {
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT,
                "Tấm %d (%s × %s cm) quá lớn để dựng file TIF ở %d DPI: %d × %d điểm ảnh, cần khoảng %d MB bộ nhớ.",
                sheetIndex + 1, cm(widthMm), cm(lengthMm), dpi, size.width(), size.height(), needMb));
        if (suggestedDpi != null) {
            text.append(" Hãy đặt DPI khoảng ").append(suggestedDpi);
            text.append(suggestedLengthMm != null ? ", hoặc" : ".");
        } else {
            text.append(" Hãy");
        }
        if (suggestedLengthMm != null) {
            text.append(" giảm chiều dài tối đa mỗi file xuống ").append(cm(suggestedLengthMm)).append(" cm.");
        } else if (suggestedDpi == null) {
            text.append(" giảm chiều dài tối đa mỗi file.");
        }
        text.append(" Bản PDF không bị giới hạn này.");
        return text.toString();
    }

    /** Vietnamese number style: decimal comma, no trailing zeros. */
    private static String cm(double mm) {
        double cm = Math.round(mm) / 10.0;
        String text = cm == Math.rint(cm) ? String.valueOf((long) cm) : String.valueOf(cm);
        return text.replace('.', ',');
    }
}
