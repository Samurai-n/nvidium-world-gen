package dev.nvidiumcache.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Arrays;
import java.util.Optional;
import java.util.*;

/** Owned by one I/O worker. No world-save paths, global preload or in-memory world index. */
public final class SnapshotStore implements AutoCloseable {
    public static final int MAX_BATCH = 16;
    private final Connection connection;
    public long written, unchanged, read, corrupt, rawBytes, storedBytes;

    public SnapshotStore(Path file) throws SQLException, IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=FULL");
            s.execute("PRAGMA busy_timeout=3000");
            s.execute("PRAGMA cache_size=-8192");
            s.execute("PRAGMA wal_autocheckpoint=256");
            s.execute("PRAGMA journal_size_limit=8388608");
            s.execute("CREATE TABLE IF NOT EXISTS snapshot(x INTEGER NOT NULL,z INTEGER NOT NULL,version INTEGER NOT NULL,hash BLOB NOT NULL,raw_size INTEGER NOT NULL,payload BLOB NOT NULL,PRIMARY KEY(x,z)) WITHOUT ROWID");
        } catch (SQLException e) { connection.close(); throw e; }
    }

    public boolean put(ChunkKey key, byte[] raw) throws SQLException, IOException {
        if (raw.length == 0 || raw.length > PayloadCodec.MAX_BYTES) throw new IOException("Invalid snapshot size");
        byte[] hash = PayloadCodec.digest(raw);
        try (PreparedStatement p = connection.prepareStatement("SELECT hash FROM snapshot WHERE x=? AND z=? AND version=1")) {
            bind(p, key);
            try (ResultSet r = p.executeQuery()) {
                if (r.next() && Arrays.equals(hash, r.getBytes(1))) { unchanged++; return false; }
            }
        }
        byte[] compressed = PayloadCodec.compress(raw);
        try (PreparedStatement p = connection.prepareStatement("INSERT INTO snapshot VALUES(?,?,1,?,?,?) ON CONFLICT(x,z) DO UPDATE SET version=excluded.version,hash=excluded.hash,raw_size=excluded.raw_size,payload=excluded.payload")) {
            bind(p, key);
            p.setBytes(3, hash);
            p.setInt(4, raw.length);
            p.setBytes(5, compressed);
            p.executeUpdate();
        }
        written++; rawBytes += raw.length; storedBytes += compressed.length;
        return true;
    }

    /** One durable commit for several completed snapshots. Only the I/O worker calls these. */
    public void beginBatch() throws SQLException { connection.setAutoCommit(false); }
    public void commitBatch() throws SQLException { connection.commit(); connection.setAutoCommit(true); }
    public void rollbackBatch() throws SQLException {
        try { connection.rollback(); } finally { connection.setAutoCommit(true); }
    }

    public Optional<byte[]> get(ChunkKey key) throws SQLException, IOException {
        return Optional.ofNullable(getBatch(List.of(key)).get(key));
    }
    /** Cheap indexed presence check. The normal read path still validates the payload and digest. */
    public java.util.Set<ChunkKey> presentBatch(List<ChunkKey> requested) throws SQLException {
        if (requested.size() > 128) throw new IllegalArgumentException("Presence batch too large");
        var keys = new ArrayList<>(new LinkedHashSet<>(requested));
        if (keys.isEmpty()) return java.util.Set.of();
        String values = String.join(",", Collections.nCopies(keys.size(), "(?,?)"));
        String sql = "WITH requested(x,z) AS (VALUES " + values + ") SELECT s.x,s.z FROM requested r JOIN snapshot s ON s.x=r.x AND s.z=r.z WHERE s.version=1 AND s.raw_size>0 AND length(s.payload)>0";
        var present = new java.util.HashSet<ChunkKey>();
        try (PreparedStatement p = connection.prepareStatement(sql)) {
            for (int i = 0; i < keys.size(); i++) { p.setInt(i * 2 + 1, keys.get(i).x()); p.setInt(i * 2 + 2, keys.get(i).z()); }
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) present.add(new ChunkKey(r.getInt(1), r.getInt(2)));
            }
        }
        return present;
    }
    /** One indexed query for a spatial group; absent keys allocate no payload. */
    public Map<ChunkKey, byte[]> getBatch(List<ChunkKey> requested) throws SQLException, IOException {
        if (requested.size() > MAX_BATCH) throw new IllegalArgumentException("Read group too large");
        var keys = new ArrayList<>(new LinkedHashSet<>(requested));
        if (keys.isEmpty()) return Map.of();
        var output = new HashMap<ChunkKey, byte[]>();
        var invalid = new ArrayList<ChunkKey>();
        String values = String.join(",", Collections.nCopies(keys.size(), "(?,?)"));
        String sql = "WITH requested(x,z) AS (VALUES " + values + ") SELECT s.x,s.z,s.version,s.hash,s.raw_size,length(s.payload),s.payload FROM requested r JOIN snapshot s ON s.x=r.x AND s.z=r.z";
        try (PreparedStatement p = connection.prepareStatement(sql)) {
            for (int i = 0; i < keys.size(); i++) { p.setInt(i * 2 + 1, keys.get(i).x()); p.setInt(i * 2 + 2, keys.get(i).z()); }
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    var key = new ChunkKey(r.getInt(1), r.getInt(2));
                    if (r.getInt(3) != 1) continue;
                    int size = r.getInt(5), compressedSize = r.getInt(6);
                    byte[] raw = null;
                    boolean rejected = size < 1 || size > PayloadCodec.MAX_BYTES || compressedSize < 1 || compressedSize > PayloadCodec.MAX_BYTES + 65536;
                    if (!rejected) {
                        try {
                            raw = PayloadCodec.decompress(r.getBytes(7), size);
                            rejected = !Arrays.equals(PayloadCodec.digest(raw), r.getBytes(4));
                        } catch (IOException e) { rejected = true; }
                    }
                    if (rejected) invalid.add(key);
                    else { output.put(key, raw); read++; }
                }
            }
        }
        for (var key : invalid) { corrupt++; delete(key); }
        return output;
    }
    public void delete(ChunkKey key) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement("DELETE FROM snapshot WHERE x=? AND z=?")) { bind(p, key); p.executeUpdate(); }
    }
    public long count() throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM snapshot")) { r.next(); return r.getLong(1); }
    }
    private static void bind(PreparedStatement p, ChunkKey key) throws SQLException { p.setInt(1, key.x()); p.setInt(2, key.z()); }
    @Override public void close() throws SQLException { connection.close(); }
}
