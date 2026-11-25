package vn.minhtri.xuongin.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Packs pieces by real outline on one sheet grid (spec 8.5). Placed pieces are stamped with their
 * gap-inflated mask; a new piece is tested with its plain mask, so touching no bit means at least one gap.
 * For each angle and column the piece drops from above to the lowest free row; the lowest top wins, then
 * the leftmost column, then the earlier angle.
 *
 * <p>Known limit: a piece cannot reach a closed cavity or one that only opens sideways.
 */
final class TrueShapeNester {

    record ShapePiece(int pieceId, int itemIndex, List<ShapeRaster.Orientation> orientations, int footprint) {}

    record Spot(ShapePiece piece, ShapeRaster.Orientation orientation, int col, int row) {}

    private final int sheetCols;
    private final int maxRows;
    private final int wordsPerRow;
    private long[] sheet;
    private int capacityRows;
    /** Skyline: one above the highest stamped cell of each column. */
    private final int[] heights;
    private int skylineMax;
    private int usedRows;

    TrueShapeNester(int sheetCols, int maxRows) {
        this.sheetCols = sheetCols;
        this.maxRows = maxRows;
        this.wordsPerRow = (sheetCols + 63) >>> 6;
        this.capacityRows = 0;
        this.sheet = new long[0];
        this.heights = new int[sheetCols];
    }

    /** Highest row used by any placed mask (exclusive). */
    int usedRows() {
        return usedRows;
    }

    List<Spot> pack(List<ShapePiece> pieces, Progress progress) {
        List<Spot> placed = new ArrayList<>(pieces.size());
        for (ShapePiece piece : pieces) {
            Spot spot = findSpot(piece);
            if (spot != null) {
                stamp(spot);
                placed.add(spot);
            }
            progress.step();
        }
        return placed;
    }

    private Spot findSpot(ShapePiece piece) {
        int tallest = 0;
        for (ShapeRaster.Orientation o : piece.orientations()) {
            tallest = Math.max(tallest, o.inflated().rows());
        }
        ensureRows((long) skylineMax + tallest + 1);

        Spot best = null;
        int bestTop = Integer.MAX_VALUE;
        for (ShapeRaster.Orientation o : piece.orientations()) {
            BitGrid mask = o.mask();
            for (int col = 0; col + mask.cols() <= sheetCols; col++) {
                int row = lowestFreeRow(mask, o.bottomProfile(), col);
                if (row >= 0 && row + mask.rows() < bestTop) {
                    bestTop = row + mask.rows();
                    best = new Spot(piece, o, col, row);
                }
            }
        }
        return best;
    }

    /** Jump above the skyline (surely free), then drop while the row below is free. -1 when it cannot fit. */
    private int lowestFreeRow(BitGrid mask, int[] bottom, int col) {
        int row = 0;
        for (int c = 0; c < bottom.length; c++) {
            if (bottom[c] >= 0) {
                row = Math.max(row, heights[col + c] - bottom[c]);
            }
        }
        if (row + mask.rows() > maxRows) {
            return -1;
        }
        while (row > 0 && !collides(mask, col, row - 1)) {
            row--;
        }
        return row;
    }

    private boolean collides(BitGrid mask, int col, int row) {
        long[] maskBits = mask.bits();
        int maskWords = mask.wordsPerRow();
        int wordShift = col >>> 6;
        int bitShift = col & 63;
        for (int r = 0; r < mask.rows(); r++) {
            int sheetRow = row + r;
            if (sheetRow >= capacityRows) {
                return false;
            }
            int maskBase = r * maskWords;
            int sheetBase = sheetRow * wordsPerRow + wordShift;
            for (int w = 0; w < maskWords; w++) {
                long value = maskBits[maskBase + w];
                if (value == 0) {
                    continue;
                }
                if ((sheet[sheetBase + w] & (value << bitShift)) != 0) {
                    return true;
                }
                if (bitShift != 0 && wordShift + w + 1 < wordsPerRow) {
                    long carry = value >>> (64 - bitShift);
                    if (carry != 0 && (sheet[sheetBase + w + 1] & carry) != 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Stamps the inflated mask (grown by the gap on every side, so it starts gapCells lower-left). */
    private void stamp(Spot spot) {
        BitGrid inflated = spot.orientation().inflated();
        int pad = (inflated.cols() - spot.orientation().mask().cols()) / 2;
        int baseCol = spot.col() - pad;
        int baseRow = spot.row() - pad;
        ensureRows((long) baseRow + inflated.rows());
        for (int r = 0; r < inflated.rows(); r++) {
            int sheetRow = baseRow + r;
            if (sheetRow < 0 || sheetRow >= capacityRows) {
                continue;
            }
            for (int c = 0; c < inflated.cols(); c++) {
                int sheetCol = baseCol + c;
                if (sheetCol < 0 || sheetCol >= sheetCols || !inflated.get(c, r)) {
                    continue;
                }
                sheet[sheetRow * wordsPerRow + (sheetCol >>> 6)] |= 1L << (sheetCol & 63);
                if (sheetRow + 1 > heights[sheetCol]) {
                    heights[sheetCol] = sheetRow + 1;
                    skylineMax = Math.max(skylineMax, sheetRow + 1);
                }
            }
        }
        usedRows = Math.max(usedRows, spot.row() + spot.orientation().mask().rows());
    }

    /** The sheet grows on demand so a long unlimited sheet does not reserve memory it never uses. */
    private void ensureRows(long wanted) {
        int target = (int) Math.min(wanted, (long) maxRows + 64);
        if (target <= capacityRows) {
            return;
        }
        int grown = Math.max(target, capacityRows * 2);
        grown = (int) Math.min(grown, (long) maxRows + 64);
        sheet = Arrays.copyOf(sheet, grown * wordsPerRow);
        capacityRows = grown;
    }
}
