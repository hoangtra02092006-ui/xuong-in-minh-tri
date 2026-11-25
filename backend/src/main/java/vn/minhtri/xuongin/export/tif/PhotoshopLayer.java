package vn.minhtri.xuongin.export.tif;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Photoshop layer data for tag 37724 (ImageSourceData, spec 10.4): one layer covering the sheet
 * whose transparency channel makes Photoshop draw a checkerboard where nothing is printed.
 *
 * <pre>
 *   "Adobe Photoshop Document Data Block\0" "8BIM" "Layr" length layerCount=1
 *   layer record: bounds (0,0)-(height,width), 5 channels -1 (transparency), 0 C, 1 M, 2 Y, 3 K,
 *                 "8BIM" "norm", opacity 255, flags 0x08, name "Layer 1"
 *   channel data in the same order, each prefixed by its compression (1 RLE, 2 ZIP)
 * </pre>
 *
 * <p>Rows are fed once, top to bottom; each channel is compressed as a stream into its own temp
 * file, so RAM stays at a few rows whatever the sheet length. Values follow the byte order of the
 * enclosing TIFF, which is big-endian here.
 */
final class PhotoshopLayer implements Closeable {

    static final String SIGNATURE = "Adobe Photoshop Document Data Block";
    static final String LAYER_NAME = "Layer 1";
    static final int CHANNELS = 5;
    static final int COMPRESSION_RLE = 1;
    static final int COMPRESSION_ZIP = 2;

    private static final int CMYK = 4;
    private static final short TRANSPARENCY_ID = -1;
    private static final int OPACITY = 255;
    /** Bit 3: "Photoshop 5.0 and later", read from the reference file. Visible, unlocked. */
    private static final int FLAGS = 0x08;
    /** Measured on a real 57 cm sheet: level 4 is the knee between size and time. */
    private static final int ZIP_LEVEL = 4;
    private static final int MAX_RUN = 128;
    private static final int STREAM_BUFFER = 1 << 16;

    private final int width;
    private final int height;
    private final boolean zip;
    private final Channel[] channels = new Channel[CHANNELS];
    private final byte[] row;
    private final ByteArrayOutputStream packed = new ByteArrayOutputStream();
    private int rows;

    PhotoshopLayer(Path tempDir, int width, int height, boolean zip) throws IOException {
        this.width = width;
        this.height = height;
        this.zip = zip;
        this.row = new byte[width];
        try {
            for (int c = 0; c < CHANNELS; c++) {
                channels[c] = new Channel(Files.createTempFile(tempDir, ".tif-layer-", ".tmp"), zip, height);
            }
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    /**
     * Adds the next row. Transparency = unchoked ink mask (255 where something prints): the colour
     * layer reaches the edge, only W1 is contracted. The four colour channels are stored inverted
     * (PSD: 0 = full ink); storing them as-is turns cream into solid black in Photoshop while the
     * composite still looks right to numeric tests.
     */
    void addRow(byte[] cmyk, int cmykOffset, byte[] mask, int maskOffset) throws IOException {
        for (int x = 0; x < width; x++) {
            row[x] = mask[maskOffset + x] != 0 ? (byte) 0xFF : 0;
        }
        channels[0].write(row);
        for (int c = 0; c < CMYK; c++) {
            for (int x = 0, at = cmykOffset + c; x < width; x++, at += CMYK) {
                row[x] = (byte) (255 - (cmyk[at] & 0xFF));
            }
            channels[c + 1].write(row);
        }
        rows++;
    }

    /** Streams the whole block into {@code out}; returns its length in bytes. */
    long writeTo(TiffFile out) throws IOException {
        if (rows != height) {
            throw new IllegalStateException("layer needs " + height + " rows, got " + rows);
        }
        long[] lengths = new long[CHANNELS];
        long data = 0;
        for (int c = 0; c < CHANNELS; c++) {
            channels[c].finish();
            lengths[c] = channels[c].length();
            data += lengths[c];
        }
        byte[] record = record(lengths);
        long body = record.length + data;
        long padded = body + (body & 1);
        long sectionLength = 2 + padded;
        if (sectionLength > Integer.MAX_VALUE) {
            throw new IOException("Photoshop layer block too large");
        }

        long start = out.position();
        out.write((SIGNATURE + '\0').getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[] {'8', 'B', 'I', 'M', 'L', 'a', 'y', 'r'});
        out.writeInt((int) sectionLength);
        out.writeShort(1);
        out.write(record);
        for (Channel channel : channels) {
            channel.copyTo(out);
        }
        if (padded != body) {
            out.write(new byte[1]);
        }
        return out.position() - start;
    }

    /**
     * Layer record. Channel lengths include the 2-byte compression field (and the RLE row table);
     * leaving those out shifts every channel and Photoshop shows noise instead of an error.
     */
    private byte[] record(long[] lengths) {
        ByteBuffer b = ByteBuffer.allocate(256);
        b.putInt(0).putInt(0).putInt(height).putInt(width);
        b.putShort((short) CHANNELS);
        for (int c = 0; c < CHANNELS; c++) {
            b.putShort(c == 0 ? TRANSPARENCY_ID : (short) (c - 1));
            b.putInt((int) lengths[c]);
        }
        b.put(new byte[] {'8', 'B', 'I', 'M', 'n', 'o', 'r', 'm'});
        b.put((byte) OPACITY).put((byte) 0).put((byte) FLAGS).put((byte) 0);

        byte[] extra = extras();
        b.putInt(extra.length).put(extra);
        byte[] result = new byte[b.position()];
        b.flip().get(result);
        return result;
    }

    /** No layer mask, no blending ranges, Pascal name padded to a multiple of 4. */
    private static byte[] extras() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[8]);
        byte[] name = LAYER_NAME.getBytes(StandardCharsets.US_ASCII);
        out.write(name.length);
        out.writeBytes(name);
        while (out.size() % 4 != 0) {
            out.write(0);
        }
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        for (Channel channel : channels) {
            if (channel == null) {
                continue;
            }
            try {
                channel.discard();
            } catch (IOException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    /** One channel: compressed bytes go to a temp file; RLE keeps only its row-length table. */
    private final class Channel {

        private final Path file;
        private final OutputStream raw;
        private final OutputStream stream;
        private final Deflater deflater;
        private final short[] rowLengths;
        private int rowIndex;
        private boolean finished;

        Channel(Path file, boolean zip, int height) throws IOException {
            this.file = file;
            this.raw = new BufferedOutputStream(Files.newOutputStream(file), STREAM_BUFFER);
            this.deflater = zip ? new Deflater(ZIP_LEVEL) : null;
            this.stream = zip ? new DeflaterOutputStream(raw, deflater, STREAM_BUFFER) : raw;
            this.rowLengths = zip ? null : new short[height];
        }

        void write(byte[] data) throws IOException {
            if (zip) {
                stream.write(data);
                return;
            }
            packed.reset();
            packBits(data, packed);
            rowLengths[rowIndex++] = (short) packed.size();
            packed.writeTo(stream);
        }

        void finish() throws IOException {
            if (!finished) {
                finished = true;
                try {
                    stream.close();
                } finally {
                    // DeflaterOutputStream never ends a Deflater it was given: free native memory.
                    if (deflater != null) {
                        deflater.end();
                    }
                }
            }
        }

        long length() throws IOException {
            return 2 + (zip ? 0 : 2L * rowLengths.length) + Files.size(file);
        }

        void copyTo(TiffFile out) throws IOException {
            out.writeShort(zip ? COMPRESSION_ZIP : COMPRESSION_RLE);
            if (!zip) {
                ByteBuffer table = ByteBuffer.allocate(rowLengths.length * 2);
                for (short n : rowLengths) {
                    table.putShort(n);
                }
                out.write(table.array());
            }
            out.copy(file);
        }

        void discard() throws IOException {
            try {
                finish();
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

    /**
     * PackBits: a run of n ≥ 3 equal bytes is written as (257 − n, byte), anything else as
     * (n − 1, n literal bytes); at most 128 bytes per packet.
     */
    static void packBits(byte[] data, ByteArrayOutputStream out) {
        int at = 0;
        while (at < data.length) {
            int run = 1;
            while (at + run < data.length && run < MAX_RUN && data[at + run] == data[at]) {
                run++;
            }
            if (run >= 3) {
                out.write(257 - run);
                out.write(data[at]);
                at += run;
                continue;
            }
            int start = at;
            int literal = 0;
            while (at < data.length && literal < MAX_RUN) {
                if (at + 2 < data.length && data[at] == data[at + 1] && data[at] == data[at + 2]) {
                    break;
                }
                at++;
                literal++;
            }
            out.write(literal - 1);
            out.write(data, start, literal);
        }
    }
}
