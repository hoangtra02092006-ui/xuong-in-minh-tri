package vn.minhtri.xuongin.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Shelf (next-fit) packer: the control packer of spec 8.2.3 and the upper bound of the length search.
 * Rotatable pieces lie landscape so more of them fit on a shelf.
 */
final class ShelfPacker {

    private final long binWidth;
    private final long binHeight;

    ShelfPacker(long binWidth, long binHeight) {
        this.binWidth = binWidth;
        this.binHeight = binHeight;
    }

    PackResult pack(List<Piece> pieces, boolean allowRotate) {
        List<PackResult.Packed> placed = new ArrayList<>(pieces.size());
        List<Piece> unplaced = new ArrayList<>();
        long shelfY = 0;
        long shelfHeight = 0;
        long cursorX = 0;

        for (Piece piece : pieces) {
            boolean canRotate = allowRotate && piece.allowRotate();
            boolean rotated = canRotate && piece.h() > piece.w();
            if (canRotate && (rotated ? piece.h() : piece.w()) > binWidth) {
                rotated = !rotated;
            }
            long w = rotated ? piece.h() : piece.w();
            long h = rotated ? piece.w() : piece.h();
            if (w > binWidth) {
                unplaced.add(piece);
                continue;
            }
            if (cursorX + w > binWidth) {
                shelfY += shelfHeight;
                shelfHeight = 0;
                cursorX = 0;
            }
            if (shelfY + h > binHeight) {
                unplaced.add(piece);
                continue;
            }
            placed.add(new PackResult.Packed(piece, cursorX, shelfY, w, h, rotated));
            cursorX += w;
            shelfHeight = Math.max(shelfHeight, h);
        }
        return PackResult.of(placed, unplaced);
    }
}
