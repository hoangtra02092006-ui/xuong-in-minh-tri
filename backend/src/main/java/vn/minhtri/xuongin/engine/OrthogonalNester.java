package vn.minhtri.xuongin.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Rectangle search of spec 8.2 and 8.3, used by ORTHOGONAL and FREE (FREE pieces carry cavities).
 *
 * <p>Works in the packer frame: pieces and strip are inflated by one gap; the caller converts back.
 */
final class OrthogonalNester {

    /** Six sort orders of spec 8.2.2; each ends with the piece id. */
    enum SortOrder {
        AREA_DESC,
        LONG_SIDE_DESC,
        SHORT_SIDE_DESC,
        HEIGHT_DESC,
        WIDTH_DESC,
        PERIMETER_DESC
    }

    static final int BINARY_SEARCH_STEPS = 40;
    static final int MAX_SHEETS = 500;
    static final int HILL_CLIMB_ROUNDS = 300;
    static final long[] HILL_CLIMB_SEEDS = {20240917L, 7L, 1337L};
    /** Forced orientations are 2^n variants; beyond 8 rotatable designs only the free variant runs. */
    static final int MAX_TYPES_FOR_FORCED_ORIENTATION = 8;
    /** Pieces whose top lies in the top 12 % of the length are moved forward during hill climbing. */
    private static final double TOP_BAND_RATIO = 0.12;
    /** Every 8th hill-climbing round targets the top band; the others only swap. */
    private static final int TOP_BAND_PERIOD = 8;
    /** Shelf + the four MaxRects heuristics. */
    private static final int PACKERS = Heuristic.values().length + 1;

    private record Variant(List<Piece> pieces, boolean allowRotate) {}

    private record Plan(List<Piece> order, Heuristic heuristic, boolean allowRotate) {}

    private final long usableWidth;
    private final boolean allowRotate;
    private final ProgressListener listener;
    private Progress progress;

    /**
     * @param usableWidth strip width in the packer frame (width - 2 margins + one gap)
     * @param allowRotate the global rotate switch
     */
    OrthogonalNester(long usableWidth, boolean allowRotate, ProgressListener listener) {
        this.usableWidth = usableWidth;
        this.allowRotate = allowRotate;
        this.listener = listener;
    }

    /** All pieces on one unlimited sheet. */
    PackResult nestOneSheet(List<Piece> pieces) {
        progress = new Progress(listener, singleSheetSteps(pieces));
        PackResult result = packSingleSheet(pieces);
        progress.finish();
        return result;
    }

    /** Sheets of at most {@code maxLength} (packer frame); progress total estimated from the net area. */
    List<PackResult> nestSheets(List<Piece> pieces, long maxLength) {
        long area = 0;
        for (Piece p : pieces) {
            area += p.netArea();
        }
        long sheetsEstimate = area / Math.max(1, usableWidth * maxLength) + 1;
        long perSheet = (long) buildVariants(pieces).size() * SortOrder.values().length * PACKERS
                + singleSheetSteps(pieces);
        progress = new Progress(listener, sheetsEstimate * perSheet);
        List<PackResult> result = packMultipleSheets(pieces, maxLength);
        progress.finish();
        return result;
    }

    private long singleSheetSteps(List<Piece> pieces) {
        return (long) buildVariants(pieces).size() * SortOrder.values().length * PACKERS
                + (long) Heuristic.values().length * HILL_CLIMB_SEEDS.length * HILL_CLIMB_ROUNDS;
    }

    // ---------------------------------------------------------------- one sheet

    /** Shortest layout of all pieces on one sheet (spec 8.2). */
    private PackResult packSingleSheet(List<Piece> pieces) {
        long lowerBound = areaLowerBound(pieces);
        long upperBound = shelfUpperBound(pieces);

        PackResult best = null;
        Map<Heuristic, PackResult> bestPerHeuristic = new EnumMap<>(Heuristic.class);
        Map<Heuristic, Plan> planPerHeuristic = new EnumMap<>(Heuristic.class);

        for (Variant variant : buildVariants(pieces)) {
            for (SortOrder order : SortOrder.values()) {
                List<Piece> sorted = sort(variant.pieces(), order);
                for (Heuristic heuristic : Heuristic.values()) {
                    PackResult candidate =
                            minimizeLength(sorted, lowerBound, upperBound, variant.allowRotate(), heuristic);
                    if (shorter(candidate, bestPerHeuristic.get(heuristic))) {
                        bestPerHeuristic.put(heuristic, candidate);
                        planPerHeuristic.put(heuristic, new Plan(sorted, heuristic, variant.allowRotate()));
                    }
                    best = shorterOf(best, candidate);
                    progress.step();
                }
                best = shorterOf(best, minimizeLengthShelf(sorted, lowerBound, upperBound, variant.allowRotate()));
                progress.step();
            }
        }
        if (best == null) {
            throw new EngineException(EngineException.Kind.NESTING_FAILED,
                    "Thuật toán không xếp được hết số hình yêu cầu. Hãy thử nới khoảng cách hoặc cho phép xoay.");
        }

        // Climb from the best plan of EACH heuristic: four starting points escape a single local optimum.
        for (Heuristic heuristic : Heuristic.values()) {
            Plan plan = planPerHeuristic.get(heuristic);
            for (long seed : HILL_CLIMB_SEEDS) {
                if (plan == null) {
                    skipSteps(HILL_CLIMB_ROUNDS);
                } else {
                    best = shorterOf(best, hillClimb(bestPerHeuristic.get(heuristic), plan, lowerBound, seed));
                }
            }
        }
        return best;
    }

    private void skipSteps(int steps) {
        for (int i = 0; i < steps; i++) {
            progress.step();
        }
    }

    /**
     * Hill climbing with a fixed seed (spec 8.2.6): perturb the order slightly, repack, keep if shorter.
     * Every 8th round moves pieces of the top band forward so they drop into gaps lower down.
     */
    private PackResult hillClimb(PackResult start, Plan plan, long lowerBound, long seed) {
        Random random = new Random(seed);
        List<Piece> current = new ArrayList<>(plan.order());
        PackResult best = start;
        int size = current.size();
        for (int round = 0; round < HILL_CLIMB_ROUNDS; round++) {
            progress.step();
            if (size < 2) {
                continue;
            }
            List<Piece> order = round % TOP_BAND_PERIOD == 0
                    ? promoteTopBand(current, best, random)
                    : new ArrayList<>(current);
            int swaps = 1 + random.nextInt(Math.max(1, size / 12));
            for (int s = 0; s < swaps; s++) {
                int i = random.nextInt(size);
                int j = random.nextInt(size);
                Piece tmp = order.get(i);
                order.set(i, order.get(j));
                order.set(j, tmp);
            }
            PackResult attempt =
                    minimizeLength(order, lowerBound, best.usedLength(), plan.allowRotate(), plan.heuristic());
            if (shorter(attempt, best)) {
                best = attempt;
                current = order;
            }
        }
        return best;
    }

    /** Nudges each top-band piece to a random earlier position (not to the front: that ruins big-first). */
    private static List<Piece> promoteTopBand(List<Piece> order, PackResult result, Random random) {
        long threshold = (long) (result.usedLength() * (1d - TOP_BAND_RATIO));
        Set<Integer> topIds = new HashSet<>();
        for (PackResult.Packed p : result.placed()) {
            if (p.top() > threshold) {
                topIds.add(p.piece().id());
            }
        }
        List<Piece> reordered = new ArrayList<>(order);
        if (topIds.isEmpty() || topIds.size() == order.size()) {
            return reordered;
        }
        for (int i = 1; i < reordered.size(); i++) {
            if (topIds.contains(reordered.get(i).id())) {
                int target = random.nextInt(i + 1);
                reordered.add(target, reordered.remove(i));
            }
        }
        return reordered;
    }

    /** Binary search on the strip length (spec 8.2.4): forcing a short strip makes the packer compact. */
    private PackResult minimizeLength(
            List<Piece> sorted, long lowerBound, long upperBound, boolean rotate, Heuristic heuristic) {
        PackResult best = null;
        long lo = lowerBound;
        long hi = upperBound;
        for (int step = 0; step < BINARY_SEARCH_STEPS && lo <= hi; step++) {
            long mid = lo + (hi - lo) / 2;
            PackResult attempt = new MaxRectsPacker(usableWidth, mid, heuristic).pack(sorted, rotate);
            if (attempt.packedEverything()) {
                best = shorterOf(best, attempt);
                hi = Math.min(mid, attempt.usedLength()) - 1;
            } else {
                lo = mid + 1;
            }
        }
        return best;
    }

    private PackResult minimizeLengthShelf(List<Piece> sorted, long lowerBound, long upperBound, boolean rotate) {
        PackResult best = null;
        long lo = lowerBound;
        long hi = upperBound;
        for (int step = 0; step < BINARY_SEARCH_STEPS && lo <= hi; step++) {
            long mid = lo + (hi - lo) / 2;
            PackResult attempt = new ShelfPacker(usableWidth, mid).pack(sorted, rotate);
            if (attempt.packedEverything()) {
                best = shorterOf(best, attempt);
                hi = Math.min(mid, attempt.usedLength()) - 1;
            } else {
                lo = mid + 1;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- several sheets

    /**
     * Fills sheets of at most {@code maxLength} (packer frame) one by one (spec 8.3): the combination that
     * places the most pieces wins, then that sheet's pieces are repacked once more to be as short as possible.
     */
    private List<PackResult> packMultipleSheets(List<Piece> pieces, long maxLength) {
        List<PackResult> sheets = new ArrayList<>();
        List<Piece> remaining = new ArrayList<>(pieces);
        while (!remaining.isEmpty()) {
            if (sheets.size() >= MAX_SHEETS) {
                throw tooManySheets(MAX_SHEETS);
            }
            PackResult best = null;
            for (Variant variant : buildVariants(remaining)) {
                for (SortOrder order : SortOrder.values()) {
                    List<Piece> sorted = sort(variant.pieces(), order);
                    for (Heuristic heuristic : Heuristic.values()) {
                        best = moreFilled(best,
                                new MaxRectsPacker(usableWidth, maxLength, heuristic).pack(sorted, variant.allowRotate()));
                        progress.step();
                    }
                    best = moreFilled(best, new ShelfPacker(usableWidth, maxLength).pack(sorted, variant.allowRotate()));
                    progress.step();
                }
            }
            if (best == null) {
                throw new EngineException(EngineException.Kind.NESTING_FAILED,
                        "Không đặt được hình nào vào tấm mới. Hãy kiểm tra lại giới hạn chiều dài và khoảng cách.");
            }
            List<Piece> onSheet = best.placed().stream().map(PackResult.Packed::piece).toList();
            PackResult compacted = packSingleSheet(onSheet);
            sheets.add(compacted.usedLength() <= best.usedLength() ? compacted : best);
            remaining = new ArrayList<>(best.unplaced());
        }
        return sheets;
    }

    static EngineException tooManySheets(int limit) {
        return new EngineException(EngineException.Kind.INVALID_REQUEST,
                "Số tấm vượt quá giới hạn an toàn (" + limit + " tấm). Hãy tăng chiều dài tối đa mỗi file.");
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The free variant (packer decides per piece), then every forced orientation per design (spec 8.2.1):
     * pieces of one design form even columns and the leftover strip stays in one piece.
     */
    private List<Variant> buildVariants(List<Piece> pieces) {
        List<Variant> variants = new ArrayList<>();
        variants.add(new Variant(pieces, allowRotate));
        if (!allowRotate) {
            return variants;
        }
        List<Integer> rotatable = pieces.stream()
                .filter(Piece::allowRotate)
                .map(Piece::itemIndex)
                .distinct()
                .sorted()
                .toList();
        if (rotatable.isEmpty() || rotatable.size() > MAX_TYPES_FOR_FORCED_ORIENTATION) {
            return variants;
        }
        for (int mask = 0; mask < 1 << rotatable.size(); mask++) {
            List<Piece> prepared = new ArrayList<>(pieces.size());
            boolean feasible = true;
            for (Piece piece : pieces) {
                int bit = rotatable.indexOf(piece.itemIndex());
                Piece p = bit >= 0 && (mask & (1 << bit)) != 0 ? piece.rotated90() : piece;
                if (p.w() > usableWidth) {
                    feasible = false;
                    break;
                }
                prepared.add(p);
            }
            if (feasible) {
                variants.add(new Variant(prepared, false));
            }
        }
        return variants;
    }

    static List<Piece> sort(List<Piece> pieces, SortOrder order) {
        Comparator<Piece> comparator = switch (order) {
            case AREA_DESC -> Comparator.comparingLong(Piece::area).reversed()
                    .thenComparing(Comparator.comparingLong(Piece::longSide).reversed());
            case LONG_SIDE_DESC -> Comparator.comparingLong(Piece::longSide).reversed()
                    .thenComparing(Comparator.comparingLong(Piece::shortSide).reversed());
            case SHORT_SIDE_DESC -> Comparator.comparingLong(Piece::shortSide).reversed()
                    .thenComparing(Comparator.comparingLong(Piece::longSide).reversed());
            case HEIGHT_DESC -> Comparator.comparingLong(Piece::h).reversed()
                    .thenComparing(Comparator.comparingLong(Piece::w).reversed());
            case WIDTH_DESC -> Comparator.comparingLong(Piece::w).reversed()
                    .thenComparing(Comparator.comparingLong(Piece::h).reversed());
            case PERIMETER_DESC -> Comparator.comparingLong((Piece p) -> p.w() + p.h()).reversed()
                    .thenComparing(Comparator.comparingLong(Piece::area).reversed());
        };
        return pieces.stream().sorted(comparator.thenComparingInt(Piece::id)).toList();
    }

    /** Net area (cavities removed, spec 8.4) over the width, and never below the tallest piece. */
    private long areaLowerBound(List<Piece> pieces) {
        long area = 0;
        long tallest = 0;
        for (Piece piece : pieces) {
            area += piece.netArea();
            tallest = Math.max(tallest, piece.shortSide());
        }
        long byArea = (area + usableWidth - 1) / usableWidth;
        return Math.max(1, Math.max(byArea, tallest));
    }

    /** One unconstrained shelf run; the sum of long sides always fits. */
    private long shelfUpperBound(List<Piece> pieces) {
        long ceiling = 1;
        for (Piece piece : pieces) {
            ceiling += piece.longSide();
        }
        PackResult shelf = new ShelfPacker(usableWidth, ceiling).pack(sort(pieces, SortOrder.AREA_DESC), allowRotate);
        return Math.max(1, shelf.packedEverything() ? shelf.usedLength() : ceiling);
    }

    private static boolean shorter(PackResult candidate, PackResult current) {
        return candidate != null && candidate.packedEverything()
                && (current == null || candidate.usedLength() < current.usedLength());
    }

    private static PackResult shorterOf(PackResult current, PackResult candidate) {
        return shorter(candidate, current) ? candidate : current;
    }

    private static PackResult moreFilled(PackResult current, PackResult candidate) {
        if (candidate.placedCount() == 0) {
            return current;
        }
        if (current == null || candidate.placedCount() > current.placedCount()) {
            return candidate;
        }
        if (candidate.placedCount() == current.placedCount() && candidate.usedLength() < current.usedLength()) {
            return candidate;
        }
        return current;
    }
}
