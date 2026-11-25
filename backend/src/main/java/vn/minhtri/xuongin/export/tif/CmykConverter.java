package vn.minhtri.xuongin.export.tif;

/** Converts opaque RGB pixels to interleaved 8-bit CMYK (0 = no ink). */
interface CmykConverter {

    /**
     * @param rgb  {@code rows × width} pixels {@code 0x??RRGGBB}, row-major from index 0
     * @param cmyk receives {@code rows × width × 4} bytes C, M, Y, K from index 0
     */
    void convert(int[] rgb, int width, int rows, byte[] cmyk);
}
