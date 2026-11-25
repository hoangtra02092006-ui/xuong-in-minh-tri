package vn.minhtri.xuongin.export.tif;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

/** Compresses one TIFF strip at a time; every strip is an independent stream. */
abstract class StripCodec implements AutoCloseable {

    static final int TIFF_LZW = 5;
    static final int TIFF_ADOBE_DEFLATE = 8;

    /** TIFF Compression tag value. */
    abstract int tiffCode();

    /** Appends the compressed form of {@code data[0, length)} to {@code out}. */
    abstract void compress(byte[] data, int length, ByteArrayOutputStream out);

    @Override
    public void close() {}

    static StripCodec of(TifCompression compression, double quality) {
        return compression == TifCompression.LZW ? new Lzw() : new Zlib(deflateLevel(quality));
    }

    /** Same mapping as the JDK TIFF writer, so quality 0.3 keeps meaning level 3. */
    static int deflateLevel(double quality) {
        return (int) (1 + 8 * quality);
    }

    static final class Zlib extends StripCodec {

        private final Deflater deflater;
        private final byte[] chunk = new byte[1 << 16];

        Zlib(int level) {
            deflater = new Deflater(level);
        }

        @Override
        int tiffCode() {
            return TIFF_ADOBE_DEFLATE;
        }

        @Override
        void compress(byte[] data, int length, ByteArrayOutputStream out) {
            deflater.reset();
            deflater.setInput(data, 0, length);
            deflater.finish();
            while (!deflater.finished()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
        }

        @Override
        public void close() {
            deflater.end();
        }
    }

    /**
     * TIFF LZW (MSB-first codes, 9 to 12 bits, "early change": the width grows one code
     * earlier than in GIF). Mirrors libtiff so every reader decodes it.
     */
    static final class Lzw extends StripCodec {

        private static final int CLEAR = 256;
        private static final int EOI = 257;
        private static final int FIRST = 258;
        private static final int MIN_BITS = 9;
        private static final int TABLE_FULL = 4094;
        private static final int HASH_SIZE = 1 << 13;

        private final int[] keys = new int[HASH_SIZE];
        private final int[] codes = new int[HASH_SIZE];

        private ByteArrayOutputStream sink;
        private int bitBuffer;
        private int bitCount;
        private int bits;
        private int next;

        @Override
        int tiffCode() {
            return TIFF_LZW;
        }

        @Override
        void compress(byte[] data, int length, ByteArrayOutputStream out) {
            sink = out;
            bitBuffer = 0;
            bitCount = 0;
            reset();
            emit(CLEAR);
            if (length == 0) {
                emit(EOI);
                flushBits();
                return;
            }
            int prefix = data[0] & 0xFF;
            for (int i = 1; i < length; i++) {
                int b = data[i] & 0xFF;
                int key = (prefix << 8) | b;
                int found = lookup(key);
                if (found >= 0) {
                    prefix = found;
                    continue;
                }
                emit(prefix);
                insert(key, next++);
                grow();
                prefix = b;
            }
            emit(prefix);
            next++;
            grow();
            emit(EOI);
            flushBits();
        }

        /** Widens codes or restarts the table right after an entry was added (libtiff order). */
        private void grow() {
            if (next == TABLE_FULL) {
                emit(CLEAR);
                reset();
            } else if (next > (1 << bits) - 1) {
                bits++;
            }
        }

        private void reset() {
            java.util.Arrays.fill(keys, -1);
            bits = MIN_BITS;
            next = FIRST;
        }

        private int slot(int key) {
            int h = (key * 0x9E3779B1) >>> 19;
            while (keys[h] != -1 && keys[h] != key) {
                h = (h + 1) & (HASH_SIZE - 1);
            }
            return h;
        }

        private int lookup(int key) {
            int h = slot(key);
            return keys[h] == key ? codes[h] : -1;
        }

        private void insert(int key, int code) {
            int h = slot(key);
            keys[h] = key;
            codes[h] = code;
        }

        private void emit(int code) {
            bitBuffer = (bitBuffer << bits) | code;
            bitCount += bits;
            while (bitCount >= 8) {
                bitCount -= 8;
                sink.write(bitBuffer >>> bitCount);
            }
            bitBuffer &= (1 << bitCount) - 1;
        }

        private void flushBits() {
            if (bitCount > 0) {
                sink.write(bitBuffer << (8 - bitCount));
            }
        }
    }
}
