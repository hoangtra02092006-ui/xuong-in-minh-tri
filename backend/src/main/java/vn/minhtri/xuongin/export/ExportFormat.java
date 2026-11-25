package vn.minhtri.xuongin.export;

import java.util.Locale;
import java.util.Map;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;

/** The three downloadable formats and their file names (spec 6.5). */
public enum ExportFormat {
    PDF("pdf", ".pdf", "application/pdf"),
    TIF("tif", ".tif", "image/tiff"),
    /** The "-cat" suffix keeps the cut file apart from the print PDF lying next to it. */
    CUT("cut", "-cat.pdf", "application/pdf");

    private static final int ID_PREFIX = 8;

    private final String key;
    private final String suffix;
    private final String contentType;

    ExportFormat(String key, String suffix, String contentType) {
        this.key = key;
        this.suffix = suffix;
        this.contentType = contentType;
    }

    public String key() {
        return key;
    }

    public String contentType() {
        return contentType;
    }

    /** @throws ApiException {@code INVALID_REQUEST} for anything but pdf, tif or cut (never silently PDF) */
    public static ExportFormat parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (ExportFormat format : values()) {
            if (format.key.equals(normalized)) {
                return format;
            }
        }
        throw new ApiException(ErrorCode.INVALID_REQUEST,
                "Định dạng \"" + value + "\" không hỗ trợ. Chỉ nhận pdf, tif hoặc cut.", Map.of("format", String.valueOf(value)));
    }

    /** {@code minh-tri-<id8>-tam01-57x100cm.pdf}: sheet numbered from 01, size rounded to whole cm. */
    public String fileName(String jobId, Sheet sheet) {
        return String.format(Locale.ROOT, "minh-tri-%s-%s-%dx%dcm%s", idPrefix(jobId), sheetTag(sheet.index()),
                Math.round(sheet.widthMm() / 10.0), Math.round(sheet.lengthMm() / 10.0), suffix);
    }

    /** {@code minh-tri-<id8>-pdf.zip}. */
    public String zipName(String jobId) {
        return "minh-tri-" + idPrefix(jobId) + "-" + key + ".zip";
    }

    /** {@code tam01} for sheet index 0. */
    public static String sheetTag(int index) {
        return String.format(Locale.ROOT, "tam%02d", index + 1);
    }

    private static String idPrefix(String jobId) {
        return jobId.length() <= ID_PREFIX ? jobId : jobId.substring(0, ID_PREFIX);
    }
}
