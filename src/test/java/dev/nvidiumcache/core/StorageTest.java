package dev.nvidiumcache.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class StorageTest {
    @TempDir Path temp;
    private byte[] payload(int seed) { byte[] data = new byte[65536]; new Random(seed).nextBytes(data); return data; }

    @Test void restartReplacementAndUnchangedContent() throws Exception {
        Path file = temp.resolve("cache.sqlite");
        ChunkKey a = new ChunkKey(-42, 71), b = new ChunkKey(Integer.MIN_VALUE, Integer.MAX_VALUE);
        try (var store = new SnapshotStore(file)) {
            assertTrue(store.put(a, payload(1)));
            assertFalse(store.put(a, payload(1)));
            assertTrue(store.put(b, payload(2)));
            assertTrue(store.put(a, payload(3)));
            assertEquals(2, store.count());
            assertEquals(1, store.unchanged);
        }
        try (var store = new SnapshotStore(file)) {
            assertArrayEquals(payload(3), store.get(a).orElseThrow());
            assertArrayEquals(payload(2), store.get(b).orElseThrow());
            assertTrue(store.get(new ChunkKey(1, 1)).isEmpty());
        }
    }

    @Test void malformedRecordIsIsolatedAndRecoverable() throws Exception {
        Path file = temp.resolve("corrupt.sqlite");
        try (var store = new SnapshotStore(file)) {
            store.put(new ChunkKey(1, 0), payload(1));
            store.put(new ChunkKey(2, 0), payload(2));
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var s = c.createStatement()) {
            s.execute("UPDATE snapshot SET raw_size=2147483647 WHERE x=1");
        }
        try (var store = new SnapshotStore(file)) {
            assertTrue(store.get(new ChunkKey(1, 0)).isEmpty());
            assertEquals(1, store.corrupt);
            assertArrayEquals(payload(2), store.get(new ChunkKey(2, 0)).orElseThrow());
            assertTrue(store.put(new ChunkKey(1, 0), payload(1)));
        }
    }

    @Test void checksumFailureDoesNotExposeIncorrectTerrain() throws Exception {
        Path file = temp.resolve("hash.sqlite");
        try (var store = new SnapshotStore(file)) { store.put(new ChunkKey(0, 0), payload(1)); }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var s = c.createStatement()) {
            s.execute("UPDATE snapshot SET hash=zeroblob(32)");
        }
        try (var store = new SnapshotStore(file)) {
            assertTrue(store.get(new ChunkKey(0, 0)).isEmpty());
            assertEquals(0, store.count());
        }
    }

    @Test void interruptedTransactionKeepsLastCommittedSnapshot() throws Exception {
        Path file = temp.resolve("crash.sqlite");
        try (var store = new SnapshotStore(file)) { store.put(new ChunkKey(1, 0), payload(1)); }
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        String cp = System.getProperty("test.runtime.classpath", System.getProperty("java.class.path"));
        Process child = new ProcessBuilder(java, "-cp", cp, CrashWriter.class.getName(), file.toString()).redirectErrorStream(true).start();
        assertTrue(child.waitFor(20, TimeUnit.SECONDS), "crash harness timed out");
        assertEquals(23, child.exitValue(), new String(child.getInputStream().readAllBytes()));
        try (var store = new SnapshotStore(file)) { assertArrayEquals(payload(1), store.get(new ChunkKey(1, 0)).orElseThrow()); }
    }
    public static class CrashWriter {
        public static void main(String[] args) throws Exception {
            var connection = DriverManager.getConnection("jdbc:sqlite:" + args[0]);
            connection.setAutoCommit(false);
            connection.createStatement().execute("UPDATE snapshot SET payload=zeroblob(100000),raw_size=1");
            Runtime.getRuntime().halt(23); // Kill only this test subprocess, before COMMIT.
        }
    }

    @Test void repeatedReplacementsReuseDatabaseSpace() throws Exception {
        Path file = temp.resolve("replace.sqlite");
        try (var store = new SnapshotStore(file)) {
            for (int i = 0; i < 100; i++) store.put(new ChunkKey(1, 1), payload(i));
            assertEquals(1, store.count());
        }
        assertTrue(Files.size(file) < 512 * 1024, "dead versions must not accumulate indefinitely");
    }
    @Test void decompressionRejectsTruncationExtraBytesAndWrongSizes() throws Exception {
        byte[] raw = payload(8), packed = PayloadCodec.compress(raw);
        assertArrayEquals(raw, PayloadCodec.decompress(packed, raw.length));
        assertThrows(java.io.IOException.class, () -> PayloadCodec.decompress(packed, Integer.MAX_VALUE));
        assertThrows(java.io.IOException.class, () -> PayloadCodec.decompress(packed, raw.length - 1));
        assertThrows(java.io.IOException.class, () -> PayloadCodec.decompress(Arrays.copyOf(packed, packed.length - 1), raw.length));
        assertThrows(java.io.IOException.class, () -> PayloadCodec.decompress(Arrays.copyOf(packed, packed.length + 1), raw.length));
    }
    @Test void batchReadsKeepHealthyNeighborsAndMissingKeysIndependent() throws Exception {
        Path file = temp.resolve("batch.sqlite");
        var a = new ChunkKey(-2, 3); var b = new ChunkKey(-1, 3); var missing = new ChunkKey(0, 3);
        try (var store = new SnapshotStore(file)) { store.put(a, payload(1)); store.put(b, payload(2)); }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var stmt = c.createStatement()) {
            stmt.execute("UPDATE snapshot SET raw_size=2147483647 WHERE x=-2");
        }
        try (var store = new SnapshotStore(file)) {
            var result = store.getBatch(java.util.List.of(a, b, missing, b));
            assertEquals(1, result.size()); assertArrayEquals(payload(2), result.get(b));
            assertEquals(1, store.corrupt);
            assertThrows(IllegalArgumentException.class, () -> store.getBatch(java.util.Collections.nCopies(17, b)));
        }
    }
}
