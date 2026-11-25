package vn.minhtri.xuongin.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import vn.minhtri.xuongin.shared.Units;

/**
 * Entry point of the nesting engine (spec 8): strip packing of a fixed-width roll, minimising length.
 * Pure Java, integer 1/100 mm, deterministic (fixed seeds, every sort ends on the piece id). Thread-safe:
 * holds no state, so one instance can serve every job.
 */
public final class NestingEngine {

    private static final Comparator<EnginePlacement> PLACEMENT_ORDER = Comparator.comparingLong(EnginePlacement::y)
            .thenComparingLong(EnginePlacement::x)
            .thenComparingInt(EnginePlacement::pieceId);
    private static final double MM_PER_CM = 10.0;

    public EngineResult nest(List<EngineItem> items, EngineParams params) {
        return nest(items, params, ProgressListener.NONE);
    }

    /**
     * Packs every print of every item.
     *
     * @throws EngineException INVALID_REQUEST (margin too large, max length below the tallest print, too many
     *                         sheets), ITEM_WIDER_THAN_SHEET, or NESTING_FAILED (count mismatch, no progress)
     */
    public EngineResult nest(List<EngineItem> items, EngineParams params, ProgressListener listener) {
        if (items.isEmpty()) {
            throw new EngineException(EngineException.Kind.INVALID_REQUEST, "Danh sách hình không được rỗng.");
        }
        if (params.sheetWidth() - 2 * params.margin() <= 0) {
            throw new EngineException(EngineException.Kind.INVALID_REQUEST,
                    "Lề biên quá lớn so với khổ ngang: không còn vùng nào để xếp hình.");
        }
        checkFits(items, params);

        List<EngineSheet> sheets;
        if (params.mode() == EngineMode.TRUE_SHAPE) {
            Progress progress = new Progress(listener, totalQuantity(items));
            sheets = TrueShapeRunner.run(items, params, progress);
            progress.finish();
        } else {
            sheets = packRectangles(items, params, listener);
        }

        EngineResult result = new EngineResult(sheets);
        long requested = totalQuantity(items);
        if (result.totalPieces() != requested) {
            throw new EngineException(EngineException.Kind.NESTING_FAILED,
                    "Sai lệch số lượng: đặt được " + result.totalPieces() + " bản trong khi yêu cầu " + requested + ".");
        }
        return result;
    }

    private static List<EngineSheet> packRectangles(List<EngineItem> items, EngineParams params,
                                                    ProgressListener listener) {
        long gap = params.gap();
        long margin = params.margin();
        // Inflated pieces need no gap outside the last column, so the strip gains one gap (spec 8.1).
        long usableWidth = params.sheetWidth() - 2 * margin + gap;
        List<Piece> pieces = buildPieces(items, params);

        OrthogonalNester nester = new OrthogonalNester(usableWidth, params.allowRotateGlobal(), listener);
        List<PackResult> packed;
        if (params.maxSheetLength() == null) {
            packed = List.of(nester.nestOneSheet(pieces));
        } else {
            // Two separate numbers (spec 8.1): real content length, and the packer's length with one gap on top.
            long maxContent = params.maxSheetLength() - 2 * margin;
            long tallest = 0;
            for (Piece p : pieces) {
                tallest = Math.max(tallest, p.allowRotate() ? Math.min(p.realW(), p.realH()) : p.realH());
            }
            if (maxContent < tallest) {
                throw tallerThanLimit(params, tallest);
            }
            packed = nester.nestSheets(pieces, maxContent + gap);
        }

        List<EngineSheet> sheets = new ArrayList<>(packed.size());
        for (PackResult result : packed) {
            List<EnginePlacement> placements = new ArrayList<>(result.placedCount());
            for (PackResult.Packed p : result.placed()) {
                Piece piece = p.piece();
                // The real print is anchored at the bottom-left of its inflated cell: every print shifts the
                // same way, so the space between two prints is exactly one gap.
                long w = p.rotated() ? piece.realH() : piece.realW();
                long h = p.rotated() ? piece.realW() : piece.realH();
                placements.add(new EnginePlacement(piece.itemIndex(), piece.id(), margin + p.x(), margin + p.y(),
                        w, h, piece.prerotated() ^ p.rotated(), 0));
            }
            // The top cell carries one gap above the real print: cut it off.
            long length = Math.max(0, result.usedLength() - gap) + 2 * margin;
            sheets.add(new EngineSheet(params.sheetWidth(), length, sorted(placements)));
        }
        return sheets;
    }

    private static List<Piece> buildPieces(List<EngineItem> items, EngineParams params) {
        List<Piece> pieces = new ArrayList<>();
        long gap = params.gap();
        int id = 0;
        for (int i = 0; i < items.size(); i++) {
            EngineItem item = items.get(i);
            boolean canRotate = params.allowRotateGlobal() && item.allowRotate();
            List<Cavity> cavities = params.mode() == EngineMode.FREE
                    ? CavityFinder.find(item.shape(), item.w(), item.h(), gap)
                    : List.of();
            for (int q = 0; q < item.quantity(); q++) {
                pieces.add(new Piece(id++, i, item.w() + gap, item.h() + gap, item.w(), item.h(),
                        canRotate, false, cavities));
            }
        }
        return pieces;
    }

    private static void checkFits(List<EngineItem> items, EngineParams params) {
        long usable = params.sheetWidth() - 2 * params.margin();
        for (EngineItem item : items) {
            boolean canRotate = params.allowRotateGlobal() && item.allowRotate();
            if (item.w() > usable && !(canRotate && item.h() <= usable)) {
                throw widerThanSheet(item, params, canRotate);
            }
        }
    }

    private static long totalQuantity(List<EngineItem> items) {
        return items.stream().mapToLong(EngineItem::quantity).sum();
    }

    static List<EnginePlacement> sorted(List<EnginePlacement> placements) {
        return placements.stream().sorted(PLACEMENT_ORDER).toList();
    }

    static EngineException widerThanSheet(EngineItem item, EngineParams params, boolean canRotate) {
        String note = canRotate ? " (đã tính cả trường hợp xoay)." : " và không được phép xoay.";
        return new EngineException(EngineException.Kind.ITEM_WIDER_THAN_SHEET,
                "Hình \"" + item.label() + "\" " + cm(item.w()) + "x" + cm(item.h()) + " cm rộng hơn khổ "
                        + cm(params.sheetWidth()) + " cm" + note,
                Map.of("fileId", item.id(), "label", item.label()));
    }

    static EngineException tallerThanLimit(EngineParams params, long tallestContent) {
        return new EngineException(EngineException.Kind.INVALID_REQUEST,
                "Chiều dài tối đa mỗi file (" + cm(params.maxSheetLength()) + " cm) nhỏ hơn hình cao nhất ("
                        + cm(tallestContent + 2 * params.margin()) + " cm, kể cả lề). Hãy tăng giới hạn chiều dài.");
    }

    /** 1/100 mm as Vietnamese cm text: up to 3 decimals, comma separator, no trailing zeros ("0,5", "57"). */
    static String cm(long centi) {
        BigDecimal value = BigDecimal.valueOf(Units.centiToMm(centi) / MM_PER_CM).stripTrailingZeros();
        return value.toPlainString().replace('.', ',');
    }
}
