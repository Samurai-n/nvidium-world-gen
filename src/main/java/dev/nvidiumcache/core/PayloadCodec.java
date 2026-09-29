package dev.nvidiumcache.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class PayloadCodec {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private PayloadCodec() {}
    public static byte[] digest(byte[] raw) {
        try { return MessageDigest.getInstance("SHA-256").digest(raw); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static byte[] compress(byte[] raw) throws IOException {
        if (raw.length > MAX_BYTES) throw new IOException("Snapshot exceeds limit");
        Deflater deflater = new Deflater(1);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
            return out.toByteArray();
        } finally { deflater.end(); }
    }
    public static byte[] decompress(byte[] compressed, int rawSize) throws IOException {
        if (rawSize < 1 || rawSize > MAX_BYTES || compressed.length > MAX_BYTES + 65536)
            throw new IOException("Invalid snapshot length");
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            byte[] raw = new byte[rawSize];
            int offset = 0;
            while (!inflater.finished() && offset < rawSize) {
                int count = inflater.inflate(raw, offset, rawSize - offset);
                if (count == 0) break;
                offset += count;
            }
            if (offset != rawSize || !inflater.finished() || inflater.getRemaining() != 0)
                throw new IOException("Truncated, oversized or trailing compressed data");
            return raw;
        } catch (DataFormatException e) { throw new IOException("Corrupt compressed snapshot", e); }
        finally { inflater.end(); }
    }
}
