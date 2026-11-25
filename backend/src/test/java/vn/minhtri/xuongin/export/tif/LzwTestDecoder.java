package vn.minhtri.xuongin.export.tif;

import java.io.ByteArrayOutputStream;

/** Straightforward TIFF LZW decoder (TIFF 6.0 section 13, early change) for round-trip tests. */
final class LzwTestDecoder {

    private LzwTestDecoder() {}

    static byte[] decode(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[][] table = new byte[4096][];
        int size = 0;
        int bits = 9;
        long buffer = 0;
        int count = 0;
        int at = 0;
        byte[] previous = null;
        while (true) {
            while (count < bits) {
                if (at >= data.length) {
                    throw new IllegalStateException("missing EOI");
                }
                buffer = (buffer << 8) | (data[at++] & 0xFF);
                count += 8;
            }
            int code = (int) ((buffer >>> (count - bits)) & ((1 << bits) - 1));
            count -= bits;
            if (code == 257) {
                return out.toByteArray();
            }
            if (code == 256) {
                for (int i = 0; i < 256; i++) {
                    table[i] = new byte[] {(byte) i};
                }
                size = 258;
                bits = 9;
                previous = null;
                continue;
            }
            byte[] entry;
            if (code < size) {
                entry = table[code];
            } else if (code == size && previous != null) {
                entry = concat(previous, previous[0]);
            } else {
                throw new IllegalStateException("bad code " + code + " with table size " + size);
            }
            out.writeBytes(entry);
            if (previous != null) {
                table[size++] = concat(previous, entry[0]);
            }
            previous = entry;
            if (size + 1 >= (1 << bits) && bits < 12) {
                bits++;
            }
        }
    }

    private static byte[] concat(byte[] head, byte tail) {
        byte[] r = java.util.Arrays.copyOf(head, head.length + 1);
        r[head.length] = tail;
        return r;
    }
}
