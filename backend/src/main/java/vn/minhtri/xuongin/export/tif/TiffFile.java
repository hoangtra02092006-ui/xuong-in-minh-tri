package vn.minhtri.xuongin.export.tif;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal big-endian ("MM") baseline TIFF writer that streams: strips and value blocks are
 * appended as they come, the single IFD goes last and the header is patched to point at it.
 * Nothing image-sized is buffered here.
 */
final class TiffFile implements Closeable {

    static final int TYPE_ASCII = 2;
    static final int TYPE_SHORT = 3;
    static final int TYPE_LONG = 4;
    static final int TYPE_RATIONAL = 5;
    static final int TYPE_UNDEFINED = 7;

    private static final int HEADER_IFD_OFFSET_POSITION = 4;
    private static final long MAX_OFFSET = 0xFFFF_FFFFL;
    private static final int COPY_BUFFER = 1 << 16;

    private final FileChannel channel;
    private final OutputStream out;
    private final List<Entry> entries = new ArrayList<>();
    private long position;

    private record Entry(int tag, int type, long count, byte[] inline, long offset) {}

    TiffFile(Path path) throws IOException {
        channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        out = new BufferedOutputStream(Channels.newOutputStream(channel), COPY_BUFFER);
        write(new byte[] {'M', 'M', 0, 42, 0, 0, 0, 0});
    }

    long position() {
        return position;
    }

    void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    void write(byte[] data, int offset, int length) throws IOException {
        out.write(data, offset, length);
        position += length;
        if (position > MAX_OFFSET) {
            throw new IOException("TIFF file exceeds 4 GB, classic TIFF offsets cannot address it");
        }
    }

    void writeShort(int value) throws IOException {
        write(new byte[] {(byte) (value >> 8), (byte) value});
    }

    void writeInt(int value) throws IOException {
        write(ByteBuffer.allocate(4).putInt(value).array());
    }

    /** TIFF values and the IFD must start on a word boundary. */
    void align() throws IOException {
        if ((position & 1) != 0) {
            write(new byte[1]);
        }
    }

    void copy(Path file) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER];
        try (var in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buffer)) > 0) {
                write(buffer, 0, n);
            }
        }
    }

    void addShorts(int tag, int... values) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(values.length * 2);
        for (int v : values) {
            data.putShort((short) v);
        }
        addData(tag, TYPE_SHORT, values.length, data.array());
    }

    void addLongs(int tag, long... values) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(values.length * 4);
        for (long v : values) {
            data.putInt((int) v);
        }
        addData(tag, TYPE_LONG, values.length, data.array());
    }

    void addRational(int tag, long numerator, long denominator) throws IOException {
        addData(tag, TYPE_RATIONAL, 1,
                ByteBuffer.allocate(8).putInt((int) numerator).putInt((int) denominator).array());
    }

    void addAscii(int tag, String text) throws IOException {
        byte[] ascii = (text + '\0').getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        addData(tag, TYPE_ASCII, ascii.length, ascii);
    }

    void addBytes(int tag, byte[] data) throws IOException {
        addData(tag, TYPE_UNDEFINED, data.length, data);
    }

    /** Registers a value already written at {@code offset} (large streamed blocks). */
    void addWritten(int tag, int type, long count, long offset) {
        entries.add(new Entry(tag, type, count, null, offset));
    }

    private void addData(int tag, int type, long count, byte[] data) throws IOException {
        if (data.length <= 4) {
            entries.add(new Entry(tag, type, count, java.util.Arrays.copyOf(data, 4), 0));
            return;
        }
        align();
        long offset = position;
        write(data);
        entries.add(new Entry(tag, type, count, null, offset));
    }

    /** Writes the IFD, points the header at it and flushes everything to disk. */
    void finish() throws IOException {
        align();
        long ifd = position;
        entries.sort(Comparator.comparingInt(Entry::tag));
        writeShort(entries.size());
        for (Entry e : entries) {
            writeShort(e.tag());
            writeShort(e.type());
            writeInt((int) e.count());
            if (e.inline() != null) {
                write(e.inline());
            } else {
                writeInt((int) e.offset());
            }
        }
        writeInt(0);
        out.flush();
        channel.write(ByteBuffer.allocate(4).putInt(0, (int) ifd), HEADER_IFD_OFFSET_POSITION);
        channel.force(false);
    }

    @Override
    public void close() throws IOException {
        try {
            out.close();
        } finally {
            channel.close();
        }
    }
}
