package vn.minhtri.xuongin.file;

import java.time.Instant;

/**
 * An uploaded source file with its measured physical size.
 *
 * @param widthMm        size used for nesting (PDF: trimmed to the content box)
 * @param sourceWidthMm  page size before trimming
 * @param contentBox     artwork box in source points; for images the full image box
 * @param occupancy      artwork mask, null for images
 * @param pageRotation   PDF /Rotate: 0, 90, 180, 270
 * @param hasAlpha       image declares transparency, or unknown (true = keep white ink). PDFs: true
 */
public record StoredFile(
        String id,
        String originalName,
        String storedPath,
        FileType type,
        double widthMm,
        double heightMm,
        double sourceWidthMm,
        double sourceHeightMm,
        ContentBox contentBox,
        OccupancyMask occupancy,
        int pageRotation,
        int pageCount,
        boolean hasAlpha,
        long sizeBytes,
        Instant uploadedAt) {

    public boolean trimmed() {
        return sourceWidthMm - widthMm > 0.5 || sourceHeightMm - heightMm > 0.5;
    }
}
