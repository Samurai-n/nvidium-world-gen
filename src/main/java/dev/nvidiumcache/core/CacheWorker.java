package dev.nvidiumcache.core;

import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Bounded ownership transfer: callers must not mutate submitted byte arrays. */
public final class CacheWorker<T> implements AutoCloseable {
    public record Request(ChunkKey key, long token) {}
    public record Result<T>(ChunkKey key, long token, T data, long readNanos, Exception error, long retainedBytes) {}
    private record Presence(List<ChunkKey> keys, CompletableFuture<Set<ChunkKey>> completed) {}
    @FunctionalInterface public interface Decoder<T> { T decode(byte[] raw, ChunkKey key) throws Exception; }
    @FunctionalInterface public interface Encoder { byte[] encode() throws Exception; }
    private record Write(long bytes, Encoder encoder, Consumer<Boolean> completed) {}
    // A previous world may still be draining while the next one starts. Never accumulate workers.
    private static final Semaphore SLOTS = new Semaphore(2);
    private final BoundedMailbox<ChunkKey, Write> writes = new BoundedMailbox<>(128, 32L << 20, Write::bytes);
    private final ArrayBlockingQueue<List<Request>> reads = new ArrayBlockingQueue<>(4);
    private final ArrayBlockingQueue<Presence> presence = new ArrayBlockingQueue<>(4);
    private static final long READY_BYTES = 64L << 20;
    private static final int READY_COUNT = 64;
    private final BoundedMailbox<Request, Result<T>> results = new BoundedMailbox<>(READY_COUNT, READY_BYTES, Result::retainedBytes);
    private final Decoder<T> decoder;
    private final ToLongFunction<T> weight;
    private final Consumer<Exception> onError;
    private final Thread thread;
    private final StorageBudget storage;
    private final java.util.Set<ChunkKey> acknowledgedWrites = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean stopping, failed, finished;
    public volatile long written, unchanged, loaded, corrupt, rawBytes, compressedBytes, maxReadNanos, readGroups, maxReadGroup;

    public static CacheWorker<byte[]> start(Path file, Consumer<Exception> onError) {
        return start(file, onError, (raw, key) -> raw, raw -> raw.length);
    }
    public static <T> CacheWorker<T> start(Path file, Consumer<Exception> onError, Decoder<T> decoder, ToLongFunction<T> weight) {
        return start(file, onError, decoder, weight, null);
    }
    public static <T> CacheWorker<T> start(Path file, Consumer<Exception> onError, Decoder<T> decoder, ToLongFunction<T> weight, StorageBudget storage) {
        if (!SLOTS.tryAcquire()) return null;
        try { return new CacheWorker<>(file, onError, decoder, weight, storage); }
        catch (RuntimeException e) { SLOTS.release(); throw e; }
    }
    private CacheWorker(Path file, Consumer<Exception> onError, Decoder<T> decoder, ToLongFunction<T> weight, StorageBudget storage) {
        this.storage = storage;
        this.onError = onError;
        this.decoder = decoder;
        this.weight = weight;
        thread = Thread.ofPlatform().daemon().name("nvidium-cache-io").unstarted(() -> run(file));
        thread.start();
    }
    public synchronized boolean save(ChunkKey key, byte[] raw) {
        return saveDeferred(key, raw.length, () -> raw);
    }
    public synchronized boolean saveDeferred(ChunkKey key, long retainedBytes, Encoder encoder) {
        return saveDeferred(key, retainedBytes, encoder, null);
    }
    public synchronized boolean saveDeferred(ChunkKey key, long retainedBytes, Encoder encoder, Consumer<Boolean> completed) {
        if (stopping || failed || !writesAllowed()) return false;
        // Generated records have acknowledgements: never replace an unacknowledged write.
        if (acknowledgedWrites.contains(key) || completed != null && writes.containsKey(key)) return false;
        boolean accepted = writes.offer(key, new Write(retainedBytes, encoder, completed));
        if (accepted && completed != null) acknowledgedWrites.add(key);
        if (accepted) LockSupport.unpark(thread);
        return accepted;
    }
    public synchronized boolean load(ChunkKey key, long token) {
        return loadBatch(List.of(new Request(key, token)));
    }
    public synchronized boolean loadBatch(List<Request> group) {
        if (stopping || failed) return false;
        if (group.isEmpty() || group.size() > SnapshotStore.MAX_BATCH) throw new IllegalArgumentException("Invalid read group");
        if (group.stream().map(Request::key).distinct().count() != group.size()) throw new IllegalArgumentException("Duplicate read key");
        boolean accepted = reads.offer(List.copyOf(group));
        if (accepted) LockSupport.unpark(thread);
        return accepted;
    }
    public Result<T> poll() {
        var result = results.poll();
        if (result != null) LockSupport.unpark(thread);
        return result == null ? null : result.value();
    }
    public int queuedWrites() { return writes.size(); }
    public long queuedBytes() { return writes.bytes(); }
    public int queuedReads() { return reads.stream().mapToInt(List::size).sum(); }
    public boolean failed() { return failed; }
    public void updateStorageLimits(long limit, long reserve) {
        if (storage != null) storage.update(limit, reserve);
        LockSupport.unpark(thread);
    }
    public CompletableFuture<Set<ChunkKey>> presentBatch(List<ChunkKey> keys) {
        if (keys.isEmpty() || keys.size() > 128) throw new IllegalArgumentException("Invalid presence batch");
        var completed = new CompletableFuture<Set<ChunkKey>>();
        if (stopping || failed || !presence.offer(new Presence(List.copyOf(keys), completed))) return null;
        LockSupport.unpark(thread);
        return completed;
    }
    public boolean finished() { return finished; }
    public boolean writesAllowed() { return storage == null || storage.allowed(); }
    public String storageStatus() { return storage == null ? "unlimited" : storage.reason().isEmpty() ? "ok" : storage.reason(); }

    private void run(Path file) {
        try (SnapshotStore store = new SnapshotStore(file)) {
            while (!stopping || writes.size() != 0) {
                // An idle, healthy cache has nothing to write: do not walk every cache directory.
                // Recheck before queued writes, or while blocked so freeing disk space can resume work.
                if (storage != null && (writes.size() != 0 || !storage.allowed())) storage.check();
                boolean work = false;
                work |= drainWrites(store);
                var query = presence.poll();
                if (query != null) {
                    try { query.completed().complete(store.presentBatch(query.keys())); }
                    catch (Exception e) { query.completed().completeExceptionally(e); }
                    work = true;
                }
                if (!stopping && results.size() < READY_COUNT) {
                    var group = reads.poll();
                    if (group != null) {
                        long start = System.nanoTime();
                        var payloads = store.getBatch(group.stream().map(Request::key).toList());
                        readGroups++; maxReadGroup = Math.max(maxReadGroup, group.size());
                        for (var request : group) {
                        if (stopping) break;
                        byte[] raw = payloads.remove(request.key());
                        T data = null;
                        Exception error = null;
                        long retained = 64;
                        if (raw != null) {
                            try {
                                data = decoder.decode(raw, request.key());
                                long measured = weight.applyAsLong(data);
                                if (measured < 0 || measured > READY_BYTES - 64) throw new IllegalArgumentException("Prepared record exceeds queue budget");
                                retained += measured;
                            } catch (Exception e) { data = null; error = e; retained = 64; }
                        }
                        long duration = System.nanoTime() - start;
                        maxReadNanos = Math.max(maxReadNanos, duration);
                        var result = new Result<>(request.key(), request.token(), data, duration, error, retained);
                        // At most one prepared result plus the remainder of one 16-record read group
                        // may wait outside the byte/count-bounded result queue. Keep writes moving.
                        while (!stopping && !results.offer(request, result)) {
                            if (storage != null && writes.size() != 0) storage.check();
                            drainWrites(store);
                            LockSupport.parkNanos(1_000_000L);
                        }
                        }
                        work = true;
                    }
                }
                written = store.written; unchanged = store.unchanged; loaded = store.read; corrupt = store.corrupt;
                rawBytes = store.rawBytes; compressedBytes = store.storedBytes;
                // New requests and shutdown unpark immediately; only storage checks need a timer.
                if (!work) LockSupport.parkNanos(1_000_000_000L);
            }
        } catch (Exception e) { failed = true; onError.accept(e); }
        finally {
            Presence abandonedQuery;
            while ((abandonedQuery = presence.poll()) != null) abandonedQuery.completed().completeExceptionally(new IllegalStateException("Cache closed"));
            var abandoned = writes.poll();
            while (abandoned != null) { completeWrite(abandoned, false); abandoned = writes.poll(); }
            reads.clear(); results.clear(); finished = true; SLOTS.release();
        }
    }

    private boolean drainWrites(SnapshotStore store) throws Exception {
        var batch = new java.util.ArrayList<BoundedMailbox.Item<ChunkKey, Write>>(8);
        for (int i = 0; i < 8; i++) {
            var entry = writes.poll();
            if (entry == null) break;
            batch.add(entry);
        }
        if (batch.isEmpty()) return false;
        boolean committed = false;
        boolean inTransaction = false;
        try {
            if (storage == null || storage.check()) {
                store.beginBatch();
                inTransaction = true;
                for (var entry : batch) store.put(entry.key(), entry.value().encoder().encode());
                store.commitBatch();
                inTransaction = false;
                committed = true;
            }
        } finally {
            try { if (inTransaction) store.rollbackBatch(); }
            finally { for (var entry : batch) completeWrite(entry, committed); }
        }
        return true;
    }

    private synchronized void completeWrite(BoundedMailbox.Item<ChunkKey, Write> entry, boolean success) {
        if (entry.value().completed() != null) {
            acknowledgedWrites.remove(entry.key()); entry.value().completed().accept(success);
        }
    }

    /** Does not stall a frame; accepted writes drain, reads are discarded. */
    @Override public synchronized void close() {
        stopping = true;
        reads.clear(); results.clear();
        LockSupport.unpark(thread);
    }
}
