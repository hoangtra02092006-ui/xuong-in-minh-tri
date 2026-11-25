package vn.minhtri.xuongin.raster;

import java.io.IOException;

/** Receives sheet rows [y, y+rows) as ARGB pixels, top to bottom, in order. */
public interface BandConsumer {
    void accept(int y, int rows, int width, int[] argb) throws IOException;
}
