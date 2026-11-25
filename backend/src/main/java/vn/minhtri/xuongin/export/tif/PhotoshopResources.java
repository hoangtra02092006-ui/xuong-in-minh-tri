package vn.minhtri.xuongin.export.tif;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Photoshop image resources for tag 34377: declare the fifth channel as a spot colour named
 * {@code W1}, solidity 100 % (spec 10.2). Plain TIFF has no place for channel names, and without
 * this block Photoshop shows "Alpha 1" and the RIP does not know it is white ink.
 *
 * <p>The four 8BIM blocks and their bytes come from the reference file the print shop made by hand
 * ({@code samples/IN6 2209.tif}). This block is always big-endian, whatever the TIFF byte order.
 */
final class PhotoshopResources {

    static final int ID_CHANNEL_NAMES = 1006;
    static final int ID_UNICODE_NAMES = 1045;
    static final int ID_ALPHA_IDENTIFIERS = 1053;
    static final int ID_DISPLAY_INFO = 1077;

    /** DisplayInfo kind 2 = spot channel; 0 would make Photoshop treat W1 as transparency. */
    static final byte KIND_SPOT = 2;
    static final int SOLIDITY_PERCENT = 100;

    private static final byte[] SIGNATURE = {'8', 'B', 'I', 'M'};
    private static final int ALPHA_IDENTIFIER = 3;
    private static final int DISPLAY_INFO_VERSION = 1;
    /** Screen preview colour (red), RGB colour space: cosmetic only, same as the reference file. */
    private static final short[] PREVIEW_RGB = {(short) 0xFFFF, 0, 0, 0};

    private PhotoshopResources() {}

    static byte[] spotChannel(String name) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        block(out, ID_CHANNEL_NAMES, pascal(name));
        block(out, ID_UNICODE_NAMES, unicode(name));
        block(out, ID_DISPLAY_INFO, displayInfo());
        block(out, ID_ALPHA_IDENTIFIERS, ByteBuffer.allocate(4).putInt(ALPHA_IDENTIFIER).array());
        return out.toByteArray();
    }

    private static byte[] pascal(String name) {
        byte[] ascii = name.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[ascii.length + 1];
        result[0] = (byte) ascii.length;
        System.arraycopy(ascii, 0, result, 1, ascii.length);
        return result;
    }

    /** Character count includes the trailing NUL, as Photoshop writes it ("W1" = 3). */
    private static byte[] unicode(String name) {
        ByteBuffer b = ByteBuffer.allocate(4 + (name.length() + 1) * 2);
        b.putInt(name.length() + 1);
        for (char c : name.toCharArray()) {
            b.putChar(c);
        }
        b.putChar('\0');
        return b.array();
    }

    private static byte[] displayInfo() {
        ByteBuffer b = ByteBuffer.allocate(17);
        b.putInt(DISPLAY_INFO_VERSION);
        b.putShort((short) 0);
        for (short c : PREVIEW_RGB) {
            b.putShort(c);
        }
        b.putShort((short) SOLIDITY_PERCENT);
        b.put(KIND_SPOT);
        return b.array();
    }

    /** Signature, id, empty padded Pascal name, length, data padded to even length. */
    private static void block(ByteArrayOutputStream out, int id, byte[] data) {
        out.writeBytes(SIGNATURE);
        out.writeBytes(new byte[] {(byte) (id >> 8), (byte) id, 0, 0});
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(data);
        if (data.length % 2 != 0) {
            out.write(0);
        }
    }
}
