package vn.minhtri.xuongin.export.tif;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Minimal, independent TIFF / Photoshop block parser used to read encoder output back in tests. */
final class TiffTestReader {

    record Field(int tag, int type, long count, byte[] value) {

        long number(int index, ByteOrder order) {
            ByteBuffer b = ByteBuffer.wrap(value).order(order);
            return switch (type) {
                case TiffFile.TYPE_SHORT -> b.getShort(index * 2) & 0xFFFF;
                case TiffFile.TYPE_LONG -> b.getInt(index * 4) & 0xFFFF_FFFFL;
                default -> throw new IllegalStateException("not numeric: type " + type);
            };
        }
    }

    record Tiff(byte[] bytes, ByteOrder order, Map<Integer, Field> fields) {

        long get(int tag) {
            return fields.get(tag).number(0, order);
        }

        long[] all(int tag) {
            Field f = fields.get(tag);
            long[] out = new long[(int) f.count()];
            for (int i = 0; i < out.length; i++) {
                out[i] = f.number(i, order);
            }
            return out;
        }

        long[] rational(int tag) {
            ByteBuffer b = ByteBuffer.wrap(fields.get(tag).value()).order(order);
            return new long[] {b.getInt(0) & 0xFFFF_FFFFL, b.getInt(4) & 0xFFFF_FFFFL};
        }

        byte[] raw(int tag) {
            return fields.get(tag).value();
        }

        /** Decodes all Deflate strips into one interleaved sample array. */
        byte[] samples() {
            long compression = get(TifEncoder.TAG_COMPRESSION);
            if (compression != StripCodec.TIFF_ADOBE_DEFLATE) {
                throw new IllegalStateException("test reader only inflates Deflate strips");
            }
            long[] offsets = all(TifEncoder.TAG_STRIP_OFFSETS);
            long[] counts = all(TifEncoder.TAG_STRIP_BYTE_COUNTS);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int s = 0; s < offsets.length; s++) {
                out.writeBytes(inflate(bytes, (int) offsets[s], (int) counts[s]));
            }
            return out.toByteArray();
        }
    }

    private TiffTestReader() {}

    static Tiff parse(byte[] bytes) {
        ByteOrder order = switch (new String(bytes, 0, 2, StandardCharsets.US_ASCII)) {
            case "MM" -> ByteOrder.BIG_ENDIAN;
            case "II" -> ByteOrder.LITTLE_ENDIAN;
            default -> throw new IllegalStateException("not a TIFF");
        };
        ByteBuffer b = ByteBuffer.wrap(bytes).order(order);
        if (b.getShort(2) != 42) {
            throw new IllegalStateException("bad magic");
        }
        int ifd = b.getInt(4);
        if (ifd % 2 != 0) {
            throw new IllegalStateException("IFD not word aligned");
        }
        int n = b.getShort(ifd) & 0xFFFF;
        Map<Integer, Field> fields = new LinkedHashMap<>();
        int previous = -1;
        for (int i = 0; i < n; i++) {
            int at = ifd + 2 + i * 12;
            int tag = b.getShort(at) & 0xFFFF;
            if (tag <= previous) {
                throw new IllegalStateException("tags not ascending at " + tag);
            }
            previous = tag;
            int type = b.getShort(at + 2) & 0xFFFF;
            long count = b.getInt(at + 4) & 0xFFFF_FFFFL;
            int size = (int) (count * typeSize(type));
            int valueAt = size <= 4 ? at + 8 : b.getInt(at + 8);
            if (size > 4 && valueAt % 2 != 0) {
                throw new IllegalStateException("value of tag " + tag + " not word aligned");
            }
            byte[] value = new byte[size];
            System.arraycopy(bytes, valueAt, value, 0, size);
            fields.put(tag, new Field(tag, type, count, value));
        }
        if (b.getInt(ifd + 2 + n * 12) != 0) {
            throw new IllegalStateException("expected a single IFD");
        }
        return new Tiff(bytes, order, fields);
    }

    private static int typeSize(int type) {
        return switch (type) {
            case TiffFile.TYPE_ASCII, TiffFile.TYPE_UNDEFINED, 1 -> 1;
            case TiffFile.TYPE_SHORT -> 2;
            case TiffFile.TYPE_LONG -> 4;
            case TiffFile.TYPE_RATIONAL -> 8;
            default -> throw new IllegalStateException("unexpected type " + type);
        };
    }

    static byte[] inflate(byte[] data, int offset, int length) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data, offset, length);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[1 << 16];
            while (!inflater.finished()) {
                int n = inflater.inflate(chunk);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalStateException("truncated zlib stream");
                }
                out.write(chunk, 0, n);
            }
            if (inflater.getRemaining() != 0) {
                throw new IllegalStateException("trailing bytes after zlib stream");
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new IllegalStateException(e);
        } finally {
            inflater.end();
        }
    }

    // ---- Photoshop image resources (34377), always big-endian ----

    record Resource(int id, byte[] data) {}

    static List<Resource> resources(byte[] block) {
        ByteBuffer b = ByteBuffer.wrap(block);
        List<Resource> out = new ArrayList<>();
        while (b.hasRemaining()) {
            byte[] sig = new byte[4];
            b.get(sig);
            if (!"8BIM".equals(new String(sig, StandardCharsets.US_ASCII))) {
                throw new IllegalStateException("bad resource signature");
            }
            int id = b.getShort() & 0xFFFF;
            int nameLength = b.get() & 0xFF;
            int namePadded = (nameLength + 1) % 2 == 0 ? nameLength + 1 : nameLength + 2;
            b.position(b.position() - 1 + namePadded);
            int size = b.getInt();
            byte[] data = new byte[size];
            b.get(data);
            if (size % 2 != 0) {
                b.get();
            }
            out.add(new Resource(id, data));
        }
        return out;
    }

    // ---- Photoshop layer block (37724) ----

    record LayerChannel(int id, long declaredLength, int compression, byte[] decoded) {}

    record Layer(int count, int top, int left, int bottom, int right, String blend, int opacity, int flags,
            String name, List<LayerChannel> channels) {}

    static Layer layer(byte[] block, ByteOrder order) {
        ByteBuffer b = ByteBuffer.wrap(block).order(order);
        String signature = PhotoshopLayer.SIGNATURE + '\0';
        byte[] sig = new byte[signature.length()];
        b.get(sig);
        if (!signature.equals(new String(sig, StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("bad data block signature");
        }
        String bim = fourCc(b, order);
        String key = fourCc(b, order);
        if (!"8BIM".equals(bim) || !"Layr".equals(key)) {
            throw new IllegalStateException("expected 8BIM Layr, got " + bim + " " + key);
        }
        long sectionLength = b.getInt() & 0xFFFF_FFFFL;
        int sectionEnd = b.position() + (int) sectionLength;
        if (sectionEnd != block.length) {
            throw new IllegalStateException("Layr length " + sectionLength + " does not match block size");
        }
        int count = b.getShort();
        if (count != 1) {
            throw new IllegalStateException("expected one layer, got " + count);
        }
        int top = b.getInt();
        int left = b.getInt();
        int bottom = b.getInt();
        int right = b.getInt();
        int channels = b.getShort();
        int[] ids = new int[channels];
        long[] lengths = new long[channels];
        for (int c = 0; c < channels; c++) {
            ids[c] = b.getShort();
            lengths[c] = b.getInt() & 0xFFFF_FFFFL;
        }
        String blendSig = fourCc(b, order);
        String blend = fourCc(b, order);
        if (!"8BIM".equals(blendSig)) {
            throw new IllegalStateException("bad blend signature");
        }
        int opacity = b.get() & 0xFF;
        b.get();
        int flags = b.get() & 0xFF;
        b.get();
        int extraLength = b.getInt();
        int extraEnd = b.position() + extraLength;
        int maskLength = b.getInt();
        b.position(b.position() + maskLength);
        int rangesLength = b.getInt();
        b.position(b.position() + rangesLength);
        int nameLength = b.get() & 0xFF;
        byte[] name = new byte[nameLength];
        b.get(name);
        if (extraLength % 4 != 0) {
            throw new IllegalStateException("extra data not padded to 4");
        }
        b.position(extraEnd);

        int width = right - left;
        int height = bottom - top;
        List<LayerChannel> decoded = new ArrayList<>();
        for (int c = 0; c < channels; c++) {
            int start = b.position();
            int compression = b.getShort();
            byte[] pixels;
            if (compression == PhotoshopLayer.COMPRESSION_ZIP) {
                pixels = inflate(block, b.position(), (int) lengths[c] - 2);
            } else if (compression == PhotoshopLayer.COMPRESSION_RLE) {
                int[] rowLengths = new int[height];
                int total = 0;
                for (int y = 0; y < height; y++) {
                    rowLengths[y] = b.getShort() & 0xFFFF;
                    total += rowLengths[y];
                }
                if (2 + 2 * height + total != lengths[c]) {
                    throw new IllegalStateException("RLE row table does not match channel length");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int at = b.position();
                for (int y = 0; y < height; y++) {
                    byte[] row = unpackBits(block, at, rowLengths[y]);
                    if (row.length != width) {
                        throw new IllegalStateException("RLE row " + y + " has " + row.length + " bytes");
                    }
                    out.writeBytes(row);
                    at += rowLengths[y];
                }
                pixels = out.toByteArray();
            } else {
                throw new IllegalStateException("unknown compression " + compression);
            }
            if (pixels.length != width * height) {
                throw new IllegalStateException("channel " + ids[c] + " decodes to " + pixels.length + " bytes");
            }
            b.position(start + (int) lengths[c]);
            decoded.add(new LayerChannel(ids[c], lengths[c], compression, pixels));
        }
        int remaining = sectionEnd - b.position();
        if (remaining < 0 || remaining > 1) {
            throw new IllegalStateException("unexpected " + remaining + " bytes after channel data");
        }
        return new Layer(count, top, left, bottom, right, blend, opacity, flags,
                new String(name, StandardCharsets.US_ASCII), decoded);
    }

    private static String fourCc(ByteBuffer b, ByteOrder order) {
        byte[] four = new byte[4];
        b.get(four);
        return new String(four, StandardCharsets.US_ASCII);
    }

    static byte[] unpackBits(byte[] data, int offset, int length) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int at = offset;
        int end = offset + length;
        while (at < end) {
            int n = data[at++];
            if (n >= 0) {
                out.write(data, at, n + 1);
                at += n + 1;
            } else if (n != -128) {
                for (int i = 0; i < 1 - n; i++) {
                    out.write(data[at]);
                }
                at++;
            }
        }
        return out.toByteArray();
    }
}
