package vn.minhtri.xuongin.raster;

/** Size of a rendered sheet in pixels. */
public record RasterSize(int width, int height) {

    public long pixels() {
        return (long) width * height;
    }
}
