package vn.minhtri.xuongin.nesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import vn.minhtri.xuongin.file.FileType;
import vn.minhtri.xuongin.file.OccupancyMask;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.nesting.model.NestItemRequest;
import vn.minhtri.xuongin.nesting.model.NestRequest;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;
import vn.minhtri.xuongin.shared.Units;

/**
 * Synchronous checks before a job is created (spec 6.3) and the size resolution of spec 8.1,
 * shared by the controller and the planner so both see the same sizes.
 */
public final class NestRequestValidator {

    /** Spec 8.4: a size edited by more than this no longer matches the occupancy mask. */
    static final double SIZE_EDIT_TOLERANCE_MM = 0.01;

    private NestRequestValidator() {}

    /**
     * Looks up every file of the request.
     *
     * @throws ApiException {@code FILE_NOT_FOUND} for the first unknown fileId
     */
    public static Map<String, StoredFile> requireFiles(NestRequest request, Function<String, StoredFile> lookup) {
        Map<String, StoredFile> files = new LinkedHashMap<>();
        for (NestItemRequest item : request.items()) {
            files.computeIfAbsent(item.fileId(), id -> {
                StoredFile file = lookup.apply(id);
                if (file == null) {
                    throw new ApiException(ErrorCode.FILE_NOT_FOUND,
                            "Không tìm thấy file " + id + ". File có thể đã bị dọn sau thời hạn lưu hoặc máy chủ đã "
                                    + "khởi động lại. Hãy tải file lên lại.",
                            Map.of("fileId", id));
                }
                return file;
            });
        }
        return files;
    }

    /** Settles the size, category, rotation and shape of every row (spec 8.1, 8.4). */
    public static List<ResolvedItem> resolve(NestRequest request, Map<String, StoredFile> files) {
        Map<String, Integer> categories = new HashMap<>();
        List<ResolvedItem> items = new ArrayList<>(request.items().size());
        for (NestItemRequest item : request.items()) {
            StoredFile file = files.get(item.fileId());
            int category = categories.computeIfAbsent(item.fileId(), id -> categories.size());
            double width = item.widthMm() != null ? item.widthMm() : file.widthMm();
            double height = item.heightMm() != null ? item.heightMm() : file.heightMm();
            boolean rotate = request.allowRotateGlobal() && item.allowRotate();
            items.add(new ResolvedItem(item.fileId(), file.originalName(), category, width, height, item.quantity(),
                    rotate, shapeOf(file, width, height)));
        }
        return items;
    }

    /** The mask only when it really describes the printed piece (spec 8.4), otherwise a solid box. */
    static OccupancyMask shapeOf(StoredFile file, double width, double height) {
        boolean vector = file.type() == FileType.PDF && file.occupancy() != null;
        boolean unedited = Math.abs(width - file.widthMm()) <= SIZE_EDIT_TOLERANCE_MM
                && Math.abs(height - file.heightMm()) <= SIZE_EDIT_TOLERANCE_MM;
        return vector && unedited && file.pageRotation() == 0 ? file.occupancy() : null;
    }

    /**
     * Refuses a piece whose narrowest usable side is wider than {@code sheet − 2 × margin}
     * (spec 6.3, {@code ITEM_WIDER_THAN_SHEET}). A margin that leaves no room at all is left to the
     * engine, which reports it through the FAILED job as the spec asks.
     */
    public static void checkWidths(NestRequest request, List<ResolvedItem> items) {
        long usable = Units.mmToCenti(request.sheetWidthMm()) - 2 * Units.mmToCenti(request.marginMm());
        if (usable <= 0) {
            return;
        }
        for (ResolvedItem item : items) {
            long w = Units.mmToCenti(item.widthMm());
            long h = Units.mmToCenti(item.heightMm());
            long narrowest = item.allowRotate() ? Math.min(w, h) : w;
            if (narrowest > usable) {
                String reason = item.allowRotate() ? "(đã tính cả trường hợp xoay)" : "và không được phép xoay";
                String message = String.format(Locale.ROOT, "Hình \"%s\" %sx%s cm rộng hơn khổ %s cm %s.",
                        item.label(), cm(item.widthMm()), cm(item.heightMm()), cm(request.sheetWidthMm()), reason);
                throw new ApiException(ErrorCode.ITEM_WIDER_THAN_SHEET, message,
                        Map.of("fileId", item.fileId(), "label", item.label()));
            }
        }
    }

    /**
     * Refuses a job with more pieces than {@code limits.maxTotalPieces()} in all, or a piece with a
     * side shorter than {@code limits.minPieceMm()} ({@code INVALID_REQUEST}).
     */
    public static void checkLimits(List<ResolvedItem> items, NestingProperties limits) {
        long total = 0;
        for (ResolvedItem item : items) {
            total += item.quantity();
        }
        if (total > limits.maxTotalPieces()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, String.format(Locale.ROOT,
                    "Tổng số bản (%d) vượt giới hạn %d bản mỗi lần ghép. Hãy chia đơn thành nhiều lần ghép.",
                    total, limits.maxTotalPieces()),
                    Map.of("totalPieces", total, "maxTotalPieces", limits.maxTotalPieces()));
        }
        for (ResolvedItem item : items) {
            if (Math.min(item.widthMm(), item.heightMm()) < limits.minPieceMm()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, String.format(Locale.ROOT,
                        "Hình \"%s\" %sx%s mm quá nhỏ: mỗi cạnh phải từ %s mm trở lên.", item.label(),
                        mm(item.widthMm()), mm(item.heightMm()), mm(limits.minPieceMm())),
                        Map.of("fileId", item.fileId(), "label", item.label()));
            }
        }
    }

    /** mm in Vietnamese style: decimal comma, at most 2 decimals, no trailing zeros (tiny sizes stay readable). */
    static String mm(double mm) {
        double rounded = Math.round(mm * 100.0) / 100.0;
        String text = rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
        return text.replace('.', ',');
    }

    /** mm to cm in Vietnamese style: decimal comma, at most 2 decimals, no trailing zeros. */
    static String cm(double mm) {
        double cm = Math.round(mm * 10.0) / 100.0;
        String text = cm == Math.rint(cm) ? String.valueOf((long) cm) : String.valueOf(cm);
        return text.replace('.', ',');
    }
}
