import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Minimal PNG encoder (RGBA8) so the harness has no external dependencies. */
public final class PngWriter {

    private PngWriter() {
    }

    public static void write(String path, int width, int height, int[] argb) throws IOException {
        byte[] raw = new byte[height * (width * 4 + 1)];
        int p = 0;
        for (int y = 0; y < height; y++) {
            raw[p++] = 0;
            for (int x = 0; x < width; x++) {
                int c = argb[y * width + x];
                raw[p++] = (byte) ((c >> 16) & 0xFF);
                raw[p++] = (byte) ((c >> 8) & 0xFF);
                raw[p++] = (byte) (c & 0xFF);
                raw[p++] = (byte) ((c >>> 24) & 0xFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        byte[] ihdr = new byte[13];
        writeInt(ihdr, 0, width);
        writeInt(ihdr, 4, height);
        ihdr[8] = 8;
        ihdr[9] = 6;
        chunk(out, "IHDR", ihdr);
        chunk(out, "IDAT", deflate(raw));
        chunk(out, "IEND", new byte[0]);
        try (OutputStream fos = new java.io.FileOutputStream(path)) {
            out.writeTo(fos);
        }
    }

    private static byte[] deflate(byte[] data) {
        Deflater d = new Deflater(6);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        while (!d.finished()) {
            int n = d.deflate(buf);
            out.write(buf, 0, n);
        }
        d.end();
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        byte[] len = new byte[4];
        writeInt(len, 0, data.length);
        out.write(len);
        byte[] t = type.getBytes("US-ASCII");
        out.write(t);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        byte[] c = new byte[4];
        writeInt(c, 0, (int) crc.getValue());
        out.write(c);
    }

    private static void writeInt(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }
}
