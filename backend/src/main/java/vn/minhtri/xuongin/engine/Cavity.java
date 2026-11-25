package vn.minhtri.xuongin.engine;

/**
 * An empty rectangle inside a piece's real box (FREE mode), relative to the box's bottom-left, 1/100 mm.
 * Every point of it is already at least one gap away from the host's artwork, so a piece placed fully
 * inside needs no further check against the host.
 */
record Cavity(long x, long y, long w, long h) {

    /**
     * The same cavity after the host turns 90 degrees counter-clockwise: (u, v) -> (hostH - v, u).
     * Uses the host's REAL height, not the gap-inflated one (an off-by-one-gap here lets a piece touch ink).
     */
    Cavity rotated90(long hostRealH) {
        return new Cavity(hostRealH - y - h, x, h, w);
    }

    long area() {
        return w * h;
    }
}
