package vn.minhtri.xuongin.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MaxRects packer for a strip of fixed width and a given maximum length (spec 8.2).
 *
 * <p>Keeps the list of maximal free rectangles. Placing a piece picks the best-scored free rectangle,
 * splits every free rectangle the piece overlaps, and prunes contained rectangles — only the newly
 * generated ones are checked (spec 8.2.8). Deterministic: ties break on (y, x, orientation).
 * The input order is used as is; sorting belongs to the caller.
 */
final class MaxRectsPacker {

    private static final class FreeRect {
        final long x;
        final long y;
        final long w;
        final long h;

        FreeRect(long x, long y, long w, long h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        long right() {
            return x + w;
        }

        long top() {
            return y + h;
        }

        boolean contains(FreeRect o) {
            return o.x >= x && o.y >= y && o.right() <= right() && o.top() <= top();
        }
    }

    private record Candidate(long x, long y, long w, long h, boolean rotated, long primary, long secondary) {}

    private static final Comparator<Piece> BY_AREA_ASC =
            Comparator.comparingLong(Piece::area).thenComparingInt(Piece::id);

    private final Heuristic heuristic;
    private final List<FreeRect> freeRects = new ArrayList<>();

    MaxRectsPacker(long binWidth, long binHeight, Heuristic heuristic) {
        this.heuristic = heuristic;
        freeRects.add(new FreeRect(0, 0, binWidth, binHeight));
    }

    /**
     * Packs in the given order. A piece that does not fit does not stop the loop; the misses are retried
     * smallest first until nothing more fits (spec 8.2.5: small pieces fill the border strips).
     */
    PackResult pack(List<Piece> pieces, boolean allowRotate) {
        List<PackResult.Packed> placed = new ArrayList<>(pieces.size());
        List<Piece> pending = new ArrayList<>();
        for (Piece piece : pieces) {
            if (!insert(piece, allowRotate, placed)) {
                pending.add(piece);
            }
        }
        pending.sort(BY_AREA_ASC);
        boolean progressed = !pending.isEmpty();
        while (progressed && !pending.isEmpty()) {
            progressed = false;
            List<Piece> still = new ArrayList<>();
            for (Piece piece : pending) {
                if (insert(piece, allowRotate, placed)) {
                    progressed = true;
                } else {
                    still.add(piece);
                }
            }
            pending = still;
        }
        return PackResult.of(placed, pending);
    }

    private boolean insert(Piece piece, boolean allowRotate, List<PackResult.Packed> placed) {
        boolean canRotate = allowRotate && piece.allowRotate() && piece.w() != piece.h();
        Candidate best = null;
        for (FreeRect free : freeRects) {
            best = better(score(free, piece.w(), piece.h(), false), best);
            if (canRotate) {
                best = better(score(free, piece.h(), piece.w(), true), best);
            }
        }
        if (best == null) {
            return false;
        }
        PackResult.Packed p = new PackResult.Packed(piece, best.x(), best.y(), best.w(), best.h(), best.rotated());
        place(p);
        placed.add(p);
        return true;
    }

    private Candidate score(FreeRect free, long w, long h, boolean rotated) {
        if (w > free.w || h > free.h) {
            return null;
        }
        long leftoverH = free.w - w;
        long leftoverV = free.h - h;
        long shortFit = Math.min(leftoverH, leftoverV);
        long longFit = Math.max(leftoverH, leftoverV);
        return switch (heuristic) {
            case BSSF -> new Candidate(free.x, free.y, w, h, rotated, shortFit, longFit);
            case BLSF -> new Candidate(free.x, free.y, w, h, rotated, longFit, shortFit);
            case BAF -> new Candidate(free.x, free.y, w, h, rotated, free.w * free.h - w * h, shortFit);
            case BL -> new Candidate(free.x, free.y, w, h, rotated, free.y + h, free.x);
        };
    }

    /** Lower score wins; full tie-break (y, x, not rotated first) keeps the result deterministic and low. */
    private static Candidate better(Candidate c, Candidate best) {
        if (c == null) {
            return best;
        }
        if (best == null) {
            return c;
        }
        if (c.primary() != best.primary()) {
            return c.primary() < best.primary() ? c : best;
        }
        if (c.secondary() != best.secondary()) {
            return c.secondary() < best.secondary() ? c : best;
        }
        if (c.y() != best.y()) {
            return c.y() < best.y() ? c : best;
        }
        if (c.x() != best.x()) {
            return c.x() < best.x() ? c : best;
        }
        return !c.rotated() && best.rotated() ? c : best;
    }

    private void place(PackResult.Packed used) {
        List<FreeRect> generated = new ArrayList<>();
        for (int i = freeRects.size() - 1; i >= 0; i--) {
            if (split(freeRects.get(i), used, generated)) {
                freeRects.remove(i);
            }
        }
        for (Cavity cavity : used.piece().cavities()) {
            // The packer may have turned the piece once more; the cavity turns with it.
            Cavity c = used.rotated() ? cavity.rotated90(used.piece().realH()) : cavity;
            generated.add(new FreeRect(used.x() + c.x(), used.y() + c.y(), c.w(), c.h()));
        }
        mergeGenerated(generated);
    }

    /** Splits {@code free} around {@code used}; returns false when they do not overlap. */
    private static boolean split(FreeRect free, PackResult.Packed used, List<FreeRect> out) {
        if (used.x() >= free.right() || used.right() <= free.x || used.y() >= free.top() || used.top() <= free.y) {
            return false;
        }
        if (used.y() > free.y) {
            out.add(new FreeRect(free.x, free.y, free.w, used.y() - free.y));
        }
        if (used.top() < free.top()) {
            out.add(new FreeRect(free.x, used.top(), free.w, free.top() - used.top()));
        }
        if (used.x() > free.x) {
            out.add(new FreeRect(free.x, free.y, used.x() - free.x, free.h));
        }
        if (used.right() < free.right()) {
            out.add(new FreeRect(used.right(), free.y, free.right() - used.right(), free.h));
        }
        return true;
    }

    /** The old list is already maximal, so only new pieces are compared (with each other, then with old ones). */
    private void mergeGenerated(List<FreeRect> generated) {
        for (int i = 0; i < generated.size(); i++) {
            for (int j = i + 1; j < generated.size(); j++) {
                if (generated.get(j).contains(generated.get(i))) {
                    generated.remove(i);
                    i--;
                    break;
                }
                if (generated.get(i).contains(generated.get(j))) {
                    generated.remove(j);
                    j--;
                }
            }
        }
        for (int i = 0; i < generated.size(); i++) {
            FreeRect fresh = generated.get(i);
            boolean redundant = false;
            for (int j = freeRects.size() - 1; j >= 0; j--) {
                FreeRect old = freeRects.get(j);
                if (old.contains(fresh)) {
                    redundant = true;
                    break;
                }
                if (fresh.contains(old)) {
                    freeRects.remove(j);
                }
            }
            if (redundant) {
                generated.remove(i);
                i--;
            }
        }
        freeRects.addAll(generated);
    }
}
