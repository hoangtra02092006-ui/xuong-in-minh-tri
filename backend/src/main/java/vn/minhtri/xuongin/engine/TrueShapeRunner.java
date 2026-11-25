package vn.minhtri.xuongin.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** TRUE_SHAPE driver: masks per design, sheet after sheet, grid cells back to 1/100 mm (spec 8.5). */
final class TrueShapeRunner {

    static final int MAX_SHEETS = 200;
    private static final long CELL = ShapeRaster.CELL;

    private TrueShapeRunner() {}

    static List<EngineSheet> run(List<EngineItem> items, EngineParams params, Progress progress) {
        long usable = params.sheetWidth() - 2 * params.margin();
        int sheetCols = (int) (usable / CELL);
        int maxRows = params.maxSheetLength() == null
                ? Integer.MAX_VALUE / 4
                : (int) Math.max(0, (params.maxSheetLength() - 2 * params.margin()) / CELL);

        List<TrueShapeNester.ShapePiece> pieces = new ArrayList<>();
        int pieceId = 0;
        for (int i = 0; i < items.size(); i++) {
            EngineItem item = items.get(i);
            boolean canRotate = params.allowRotateGlobal() && item.allowRotate();
            List<ShapeRaster.Orientation> orientations =
                    ShapeRaster.orientations(item.shape(), item.w(), item.h(), canRotate, params.gap()).stream()
                            .filter(o -> o.mask().cols() <= sheetCols)
                            .toList();
            if (orientations.isEmpty()) {
                throw NestingEngine.widerThanSheet(item, params, canRotate);
            }
            List<ShapeRaster.Orientation> fitting =
                    orientations.stream().filter(o -> o.mask().rows() <= maxRows).toList();
            if (fitting.isEmpty()) {
                throw NestingEngine.tallerThanLimit(params, minRows(orientations) * CELL);
            }
            int footprint = fitting.get(0).mask().occupiedCells();
            for (int q = 0; q < item.quantity(); q++) {
                pieces.add(new TrueShapeNester.ShapePiece(pieceId++, i, fitting, footprint));
            }
        }
        // Big first: small pieces can still slip into what is left, never the other way round.
        pieces.sort(Comparator.comparingInt(TrueShapeNester.ShapePiece::footprint).reversed()
                .thenComparingInt(TrueShapeNester.ShapePiece::pieceId));

        List<EngineSheet> sheets = new ArrayList<>();
        List<TrueShapeNester.ShapePiece> remaining = pieces;
        while (!remaining.isEmpty()) {
            if (sheets.size() >= MAX_SHEETS) {
                throw OrthogonalNester.tooManySheets(MAX_SHEETS);
            }
            TrueShapeNester nester = new TrueShapeNester(sheetCols, maxRows);
            List<TrueShapeNester.Spot> spots = nester.pack(remaining, progress);
            if (spots.isEmpty()) {
                throw new EngineException(EngineException.Kind.NESTING_FAILED,
                        "Không đặt được hình nào vào tấm mới ở chế độ xếp theo hình thật.");
            }
            sheets.add(toSheet(spots, params));
            Set<Integer> done = new HashSet<>();
            spots.forEach(s -> done.add(s.piece().pieceId()));
            remaining = remaining.stream().filter(p -> !done.contains(p.pieceId())).toList();
        }
        return sheets;
    }

    private static int minRows(List<ShapeRaster.Orientation> orientations) {
        return orientations.stream().mapToInt(o -> o.mask().rows()).min().orElse(0);
    }

    private static EngineSheet toSheet(List<TrueShapeNester.Spot> spots, EngineParams params) {
        long margin = params.margin();
        long contentTop = 0;
        List<EnginePlacement> placements = new ArrayList<>(spots.size());
        for (TrueShapeNester.Spot spot : spots) {
            ShapeRaster.Orientation o = spot.orientation();
            long x = margin + spot.col() * CELL + o.dx();
            long y = margin + spot.row() * CELL + o.dy();
            placements.add(new EnginePlacement(spot.piece().itemIndex(), spot.piece().pieceId(),
                    x, y, o.boxW(), o.boxH(), false, o.angleDeg()));
            contentTop = Math.max(contentTop, y - margin + o.boxH());
        }
        return new EngineSheet(params.sheetWidth(), contentTop + 2 * margin, NestingEngine.sorted(placements));
    }
}
