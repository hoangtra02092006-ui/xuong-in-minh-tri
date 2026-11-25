package vn.minhtri.xuongin.engine;

import java.util.List;

/** Result of one packer run on one sheet; usedLength is the top of the highest inflated cell. */
record PackResult(List<Packed> placed, List<Piece> unplaced, long usedLength) {

    /** A piece in the packer's frame: bottom-left of its inflated cell, size after rotation. */
    record Packed(Piece piece, long x, long y, long w, long h, boolean rotated) {

        long right() {
            return x + w;
        }

        long top() {
            return y + h;
        }
    }

    static PackResult of(List<Packed> placed, List<Piece> unplaced) {
        long used = 0;
        for (Packed p : placed) {
            used = Math.max(used, p.top());
        }
        return new PackResult(placed, unplaced, used);
    }

    int placedCount() {
        return placed.size();
    }

    boolean packedEverything() {
        return unplaced.isEmpty();
    }
}
