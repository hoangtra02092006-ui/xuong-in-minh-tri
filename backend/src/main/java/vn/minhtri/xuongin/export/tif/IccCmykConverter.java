package vn.minhtri.xuongin.export.tif;

import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;

/**
 * RGB to CMYK through a real ICC profile (spec 10.3), never a hand formula.
 *
 * <p>The target raster is a fresh, contiguous 4-band image that starts at byte 0: ColorConvertOp
 * ignores custom strides, which once produced striped output.
 */
final class IccCmykConverter implements CmykConverter {

    private static final int[] RGB_MASKS = {0xFF0000, 0xFF00, 0xFF};
    private static final int CMYK_BANDS = 4;

    private final ComponentColorModel cmykModel;
    private final DirectColorModel rgbModel = new DirectColorModel(24, RGB_MASKS[0], RGB_MASKS[1], RGB_MASKS[2]);
    private final ColorConvertOp op = new ColorConvertOp(null);

    IccCmykConverter(ICC_Profile profile) {
        this.cmykModel = new ComponentColorModel(
                new ICC_ColorSpace(profile), false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
    }

    @Override
    public void convert(int[] rgb, int width, int rows, byte[] cmyk) {
        int pixels = width * rows;
        WritableRaster source = Raster.createWritableRaster(
                new SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, width, rows, RGB_MASKS),
                new DataBufferInt(rgb, pixels), new Point());
        BufferedImage from = new BufferedImage(rgbModel, source, false, null);

        int[] offsets = {0, 1, 2, 3};
        WritableRaster target = Raster.createWritableRaster(
                new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, width, rows, CMYK_BANDS,
                        width * CMYK_BANDS, offsets),
                new DataBufferByte(cmyk, pixels * CMYK_BANDS), new Point());
        BufferedImage to = new BufferedImage(cmykModel, target, false, null);

        op.filter(from, to);
    }
}
