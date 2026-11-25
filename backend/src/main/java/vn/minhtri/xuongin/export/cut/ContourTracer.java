package vn.minhtri.xuongin.export.cut;

import java.util.Arrays;

/**
 * Traces every boundary of an ink mask (spec 10.5 step 4): outer edges and hole edges (the
 * counter of o, a, ê), along pixel edges, with vertices on pixel corners.
 *
 * <p>Memory: no extra whole-sheet array. Each boundary edge belongs to exactly one ink pixel
 * (the ink side), so "edge already traced" is kept in four spare bits of that pixel's mask
 * byte. Bit 0 stays the ink flag ({@code InkMask.INK = 1}); the spare bits are cleared again
 * before returning, so the mask is unchanged afterwards.
 *
 * <p>Orientation: ink is always on the right of the walking direction (image coordinates,
 * y down), so outer boundaries have a positive area and holes a negative one. Ink pixels that
 * touch only diagonally are separate pieces (the walk turns right at a saddle corner), so
 * every contour is a simple polygon.
 *
 * <p>Only corners are emitted: straight runs are merged while walking (the O(n) collinear
 * merge of step 6), so Douglas–Peucker never sees one vertex per pixel of a straight edge.
 */
final class ContourTracer {

    /** Receives one closed contour: {@code n} corners as x0,y0,x1,y1,… in pixel corners. */
    interface Sink {
        /**
         * @param xy          interleaved corner coordinates, valid up to index {@code 2n}
         * @param n           number of corners (at least 4)
         * @param doubledArea twice the signed area in px²: positive = outer, negative = hole
         */
        void accept(int[] xy, int n, long doubledArea);
    }

    private static final int INK_BIT = 1;
    private static final int TOP = 1 << 1;
    private static final int RIGHT = 1 << 2;
    private static final int BOTTOM = 1 << 3;
    private static final int LEFT = 1 << 4;
    private static final int SEEN_BITS = TOP | RIGHT | BOTTOM | LEFT;

    /** Headings E, S, W, N (clockwise on screen); +1 = turn right. */
    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DY = {0, 1, 0, -1};

    private static final int EAST = 0;

    private final byte[] mask;
    private final int width;
    private final int height;
    private int[] xy = new int[64];

    private ContourTracer(byte[] mask, int width, int height) {
        this.mask = mask;
        this.width = width;
        this.height = height;
    }

    /**
     * Calls {@code sink} once per boundary, in scan order (top to bottom, left to right of the
     * first top edge), so the output is deterministic.
     *
     * @param mask row-major {@code width × height}; a pixel is ink when bit 0 is set
     */
    static void trace(byte[] mask, int width, int height, Sink sink) {
        if (mask.length < (long) width * height) {
            throw new IllegalArgumentException("mask smaller than " + width + "x" + height);
        }
        ContourTracer tracer = new ContourTracer(mask, width, height);
        try {
            tracer.scan(sink);
        } finally {
            tracer.clearSeenBits();
        }
    }

    private void scan(Sink sink) {
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int value = mask[row + x];
                // Every closed boundary has at least one top edge, so seeding from unseen
                // top edges finds every outer boundary and every hole.
                if ((value & INK_BIT) != 0 && (value & TOP) == 0 && !ink(x, y - 1)) {
                    follow(x, y, sink);
                }
            }
        }
    }

    private void follow(int startX, int startY, Sink sink) {
        int n = 0;
        long doubledArea = 0;
        int vx = startX;
        int vy = startY;
        int heading = EAST;
        while (markSeen(vx, vy, heading)) {
            vx += DX[heading];
            vy += DY[heading];
            int next = nextHeading(vx, vy, heading);
            if (next != heading) {
                n = addCorner(n, vx, vy);
                heading = next;
            }
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            doubledArea += (long) xy[2 * i] * xy[2 * j + 1] - (long) xy[2 * j] * xy[2 * i + 1];
        }
        sink.accept(xy, n, doubledArea);
    }

    /**
     * Heading at vertex (vx, vy) after arriving with {@code heading}, from the two pixels in
     * front of the vertex: front-right empty → turn right; front-right ink and front-left
     * empty → straight on; both ink → turn left.
     */
    private int nextHeading(int vx, int vy, int heading) {
        boolean frontRight;
        boolean frontLeft;
        switch (heading) {
            case 0 -> { // east
                frontLeft = ink(vx, vy - 1);
                frontRight = ink(vx, vy);
            }
            case 1 -> { // south
                frontLeft = ink(vx, vy);
                frontRight = ink(vx - 1, vy);
            }
            case 2 -> { // west
                frontLeft = ink(vx - 1, vy);
                frontRight = ink(vx - 1, vy - 1);
            }
            default -> { // north
                frontLeft = ink(vx - 1, vy - 1);
                frontRight = ink(vx, vy - 1);
            }
        }
        if (!frontRight) {
            return (heading + 1) & 3;
        }
        return frontLeft ? (heading + 3) & 3 : heading;
    }

    /**
     * Marks the edge leaving (vx, vy) with {@code heading} as traced.
     *
     * @return false when it was already traced: the contour is closed
     */
    private boolean markSeen(int vx, int vy, int heading) {
        int index;
        int bit;
        switch (heading) {
            case 0 -> { // east: top edge of the pixel below
                index = vy * width + vx;
                bit = TOP;
            }
            case 1 -> { // south: right edge of the pixel on the left
                index = vy * width + vx - 1;
                bit = RIGHT;
            }
            case 2 -> { // west: bottom edge of the pixel above
                index = (vy - 1) * width + vx - 1;
                bit = BOTTOM;
            }
            default -> { // north: left edge of the pixel on the right
                index = (vy - 1) * width + vx;
                bit = LEFT;
            }
        }
        if ((mask[index] & bit) != 0) {
            return false;
        }
        mask[index] |= (byte) bit;
        return true;
    }

    private boolean ink(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && (mask[y * width + x] & INK_BIT) != 0;
    }

    private int addCorner(int n, int vx, int vy) {
        if (2 * n + 2 > xy.length) {
            xy = Arrays.copyOf(xy, xy.length * 2);
        }
        xy[2 * n] = vx;
        xy[2 * n + 1] = vy;
        return n + 1;
    }

    private void clearSeenBits() {
        for (int i = 0; i < mask.length; i++) {
            mask[i] &= (byte) ~SEEN_BITS;
        }
    }
}
