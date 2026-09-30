package dev.nvidiumcache.fabric;

import dev.nvidiumcache.core.*;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CacheSession implements AutoCloseable {
    public final ClientLevel level;
    private final CacheWorker<ChunkSnapshotCodec.Prepared> io;
    private final Map<ChunkKey, VisualChunk> resident = new ConcurrentHashMap<>();
    private final Map<ChunkKey, Long> pending = new HashMap<>();
    private final DirtyTracker dirty = new DirtyTracker(8192);
    private final Map<ChunkKey, Long> retryAfter = new HashMap<>();
    private final Set<ChunkKey> newlySaved = new LinkedHashSet<>();
    private boolean rescanRequested;
    private long meshRevision;
    private long backgroundScanColumns;
    private final Map<LevelChunk, Boolean> observed = new WeakHashMap<>();
    private SpatialGroupCursor loadCursor;
    private SpatialCursor discoveryCursor;
    private List<ChunkKey> waitingGroup;
    private int waitingOffset;
    private CacheConfig config = WorldCacheClient.config;
    private volatile boolean closed;
    private boolean trimPending;
    private int centerX = Integer.MIN_VALUE, centerZ, radius;
    private long tick, nextToken, residentBytes, captured, restored, missed, rejected, deferred, slowTicks, meshBytes, meshSections;
    private long maxCaptureNanos, maxRestoreNanos, firstMeshNanos, maxRestoredPerTick;
    private double tickIntervalMs = 50;
    private long lastTickNanos;
    private int capturesThisTick;
    private long tickDeadline;
    public volatile boolean paused;

    private CacheSession(ClientLevel level, CacheWorker<ChunkSnapshotCodec.Prepared> io) { this.level = level; this.io = io; }
    public static CacheSession open(ClientLevel level, Path path) {
        var context = ChunkSnapshotCodec.decodeContext(level);
        var server = Minecraft.getInstance().getSingleplayerServer();
        Path save = server == null ? null : server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        var worker = CacheWorker.start(path, error -> WorldCacheClient.LOGGER.error("Cache I/O stopped; world data unaffected", error),
            (raw, key) -> ChunkSnapshotCodec.prepare(raw, key, context), ChunkSnapshotCodec.Prepared::estimatedBytes,
            new StorageBudget(path.toAbsolutePath().getParent().getParent(), (long) WorldCacheClient.config.cacheDiskMiB << 20,
                (long) WorldCacheClient.config.freeDiskMiB << 20, save));
        if (worker == null) return null;
        WorldCacheClient.LOGGER.info("Opening visual cache {}", path);
        return new CacheSession(level, worker);
    }
    public VisualChunk get(int x, int z) { return resident.get(new ChunkKey(x, z)); }
    public java.util.concurrent.CompletableFuture<java.util.Set<ChunkKey>> checkCached(List<ChunkKey> keys) {
        return closed ? null : io.presentBatch(keys);
    }
    public boolean generationReady() { return !closed && !paused && !io.failed() && io.writesAllowed() && io.queuedWrites() < 96 && io.queuedBytes() < (24L << 20); }
    public String storageStatus() {
        if (io.failed()) return "gravação interrompida por erro";
        return switch (io.storageStatus()) {
        case "checking" -> "verificando espaço";
        case "cache limit" -> "limite do cache atingido";
        case "free space reserve" -> "reserva de espaço livre atingida";
        case "world save free space reserve" -> "reserva de espaço do save atingida";
        case "storage check failed" -> "não foi possível verificar o disco";
        default -> io.storageStatus();
    }; }
    public boolean saveGenerated(ChunkSnapshotCodec.Captured snapshot, java.util.function.Consumer<Boolean> completed) {
        return generationReady() && io.saveDeferred(snapshot.key(), snapshot.retainedBytes(), snapshot::encode, success -> {
            if (success) Minecraft.getInstance().execute(() -> refreshSaved(snapshot.key()));
            completed.accept(success);
        });
    }
    public DataLayer light(LightLayer type, SectionPos pos) {
        VisualChunk chunk = get(pos.x(), pos.z());
        if (chunk == null) return null;
        int index = pos.y() - level.getMinSectionY() + 1;
        if (index < 0 || index >= chunk.blockLight.length) return null;
        return type == LightLayer.BLOCK ? chunk.blockLight[index] : chunk.skyLight[index];
    }
    public void changed(int x, int z) {
        meshRevision++;
        if (!dirty.mark(new ChunkKey(x, z), tick)) deferred++;
    }
    public long meshRevision() { return meshRevision; }
    public void backgroundColumnScanned() { backgroundScanColumns++; }
    public long backgroundScanColumns() { return backgroundScanColumns; }
    private void refreshSaved(ChunkKey key) {
        if (closed) return;
        if (!inRange(key)) return;
        retryAfter.remove(key);
        pending.remove(key); // An older read must not hide a just-committed record.
        if (newlySaved.size() < 256) newlySaved.add(key);
        else rescanRequested = true;
    }
    public void authoritative(int x, int z) {
        ChunkKey key = new ChunkKey(x, z);
        pending.remove(key); retryAfter.remove(key);
        // Remove before the real packet is installed; never let an old asynchronous read win.
        removeVisual(key, true);
        changed(x, z);
    }
    public void uploaded(int x, int z, int bytes) {
        VisualChunk chunk = get(x, z);
        if (chunk != null) {
            meshRevision++;
            meshBytes += bytes; meshSections++;
            if (firstMeshNanos == 0) firstMeshNanos = System.nanoTime() - chunk.installedNanos;
        }
    }
    public void keepTracked(int x, int z) {
        refreshSaved(new ChunkKey(x, z));
        if (get(x, z) != null) ChunkTrackerHolder.get(level).onChunkStatusAdded(x, z, 3);
    }
    public void tick(Minecraft client) {
        tick++;
        capturesThisTick = 0;
        if (client.player == null || io.failed()) return;
        long now = System.nanoTime();
        if (lastTickNanos != 0) {
            double elapsed = (now - lastTickNanos) / 1e6;
            tickIntervalMs = elapsed > 1000 ? 50 : tickIntervalMs * 0.9 + elapsed * 0.1;
        }
        lastTickNanos = now;
        int cx = client.player.chunkPosition().x(), cz = client.player.chunkPosition().z();
        int requestedRadius = config.radiusChunks;
        if (cx != centerX || cz != centerZ || requestedRadius != radius) rebuildCandidates(cx, cz, requestedRadius);
        if (paused) return;
        double budgetMillis = tickIntervalMs > 55 ? Math.min(0.5, config.tickBudgetMillis) : config.tickBudgetMillis;
        long deadline = now + (long) (budgetMillis * 1e6);
        tickDeadline = deadline;
        // Trim farthest data gradually when the player moves or reduces limits live.
        for (int i = 0; trimPending && i < 4 && System.nanoTime() < deadline; i++) {
            var farthest = resident.keySet().stream().filter(key -> !inRange(key)).findFirst().orElse(null);
            if (farthest == null && (resident.size() > config.maxResidentChunks || residentBytes > (long) config.residentMiB << 20))
                farthest = farthestResident();
            if (farthest == null) { trimPending = false; break; }
            removeVisual(farthest, false);
        }
        // A tick budget is cooperative: a single chunk operation cannot be preempted. Measure overruns.
        for (int i = 0; i < 4 && System.nanoTime() < deadline; i++) {
            ChunkKey save = dirty.due(tick, config.debounceTicks, config.maxDirtyAgeTicks);
            if (save == null) break;
            capture(save);
        }
        if (tickIntervalMs < 75) {
            long before = restored;
            // Palettes/NBT were prepared off-thread. Install bounded batches within the client budget.
            for (int i = 0; i < 128 && restored - before < config.restoresPerTick && System.nanoTime() < deadline; i++) if (!consumeOne()) break;
            maxRestoredPerTick = Math.max(maxRestoredPerTick, restored - before);
        }
        if (System.nanoTime() < deadline && tickIntervalMs < 75) requestLoads();
        // Discover already-loaded chunks when joining and recover missed dirtiness after overload.
        if (discoveryCursor != null) {
            for (int i = 0; i < 8; i++) {
                ChunkKey key = discoveryCursor.nextInPass();
                if (key == null) { discoveryCursor = null; break; }
                LevelChunk existing = real(key);
                if (existing != null && !observed.containsKey(existing) && dirty.mark(key, tick)) observed.put(existing, Boolean.TRUE);
            }
        }
        if (System.nanoTime() > deadline) slowTicks++;
    }
    private LevelChunk real(ChunkKey key) {
        LevelChunk chunk = level.getChunkSource().getChunk(key.x(), key.z(), ChunkStatus.FULL, false);
        return chunk == null || chunk instanceof VisualChunk ? null : chunk;
    }
    private void capture(ChunkKey key) {
        if (capturesThisTick >= 4 || (tickDeadline != 0 && System.nanoTime() > tickDeadline)) { deferred++; return; }
        LevelChunk chunk = real(key);
        if (chunk == null) { dirty.remove(key); return; }
        if (io.queuedWrites() >= 120 || io.queuedBytes() > 24L << 20) { deferred++; return; }
        try {
            capturesThisTick++;
            long start = System.nanoTime();
            var snapshot = ChunkSnapshotCodec.freeze(chunk);
            maxCaptureNanos = Math.max(maxCaptureNanos, System.nanoTime() - start);
            if (io.saveDeferred(key, snapshot.retainedBytes(), snapshot::encode,
                    success -> { if (success) Minecraft.getInstance().execute(() -> refreshSaved(key)); })) { captured++; dirty.remove(key); }
            else deferred++;
        } catch (Exception e) {
            dirty.remove(key); rejected++;
            WorldCacheClient.LOGGER.warn("Cannot capture visual chunk {}", key, e);
        }
    }
    public void beforeDrop(LevelChunk chunk) {
        if (chunk == null || chunk instanceof VisualChunk || chunk.getLevel() != level) return;
        // Bounded best effort. Incremental saves normally captured it before unload.
        capture(new ChunkKey(chunk.getPos().x(), chunk.getPos().z()));
    }
    private void rebuildCandidates(int x, int z, int newRadius) {
        trimPending = true;
        centerX = x; centerZ = z; radius = newRadius;
        loadCursor = new SpatialGroupCursor(x, z, radius, config.groupSide);
        waitingGroup = null;
        discoveryCursor = new SpatialCursor(x, z, radius);
        pending.keySet().removeIf(key -> !inRange(key));
        retryAfter.keySet().removeIf(key -> !inRange(key));
        newlySaved.removeIf(key -> !inRange(key));
        rescanRequested = false;
    }
    private boolean inRange(ChunkKey key) { return Math.abs((long) key.x() - centerX) <= radius && Math.abs((long) key.z() - centerZ) <= radius; }
    private void requestLoads() {
        if (loadCursor == null && rescanRequested) {
            loadCursor = new SpatialGroupCursor(centerX, centerZ, radius, config.groupSide);
            rescanRequested = false;
        }
        int groups = 0, scanned = 0;
        boolean atCapacity = resident.size() >= config.maxResidentChunks || residentBytes >= (long) config.residentMiB << 20;
        ChunkKey farthest = atCapacity ? farthestResident() : null;
        // Keep one update ready and another in flight at higher speeds; byte budgets still apply.
        int pendingLimit = config.restoresPerTick > 32 ? 128 : 64;
        while (pending.size() <= pendingLimit - SnapshotStore.MAX_BATCH && groups < 4 && scanned++ < 32
                && System.nanoTime() < tickDeadline && (loadCursor != null || waitingGroup != null || !newlySaved.isEmpty())) {
            if (waitingGroup == null) {
                if (!newlySaved.isEmpty()) {
                    waitingGroup = newlySaved.stream().sorted(java.util.Comparator.comparingLong(k -> k.distanceSquared(centerX, centerZ)))
                        .limit(SnapshotStore.MAX_BATCH).toList();
                    newlySaved.removeAll(waitingGroup);
                } else {
                    waitingGroup = loadCursor.nextInPass();
                    if (waitingGroup == null) { loadCursor = null; continue; }
                }
                waitingOffset = 0;
            }
            int end = Math.min(waitingOffset + SnapshotStore.MAX_BATCH, waitingGroup.size());
            var requests = new ArrayList<CacheWorker.Request>(SnapshotStore.MAX_BATCH);
            for (ChunkKey key : waitingGroup.subList(waitingOffset, end)) {
                if (resident.containsKey(key) || pending.containsKey(key) || retryAfter.getOrDefault(key, 0L) > tick || real(key) != null) continue;
                if (atCapacity && (farthest == null || farthest.distanceSquared(centerX, centerZ) <= key.distanceSquared(centerX, centerZ))) continue;
                requests.add(new CacheWorker.Request(key, ++nextToken));
            }
            if (requests.isEmpty()) { waitingOffset = end; if (end == waitingGroup.size()) waitingGroup = null; continue; }
            if (!io.loadBatch(requests)) break;
            for (var request : requests) pending.put(request.key(), request.token());
            waitingOffset = end;
            if (end == waitingGroup.size()) waitingGroup = null;
            groups++;
        }
    }
    private boolean consumeOne() {
        var result = io.poll();
        if (result == null) return false;
        if (!Objects.equals(pending.get(result.key()), result.token())) return true;
        pending.remove(result.key());
        if (!inRange(result.key()) || real(result.key()) != null) return true;
        try {
            if (result.error() != null) throw result.error();
            if (result.data() == null) { missed++; retryAfter.put(result.key(), tick + 600); return true; }
            long start = System.nanoTime();
            VisualChunk chunk = result.data().installable(level);
            if (resident.size() >= config.maxResidentChunks || residentBytes + chunk.estimatedBytes > (long) config.residentMiB << 20) {
                // Do not keep far data while refusing a nearer candidate.
                var farthest = farthestResident();
                if (farthest != null && farthest.distanceSquared(centerX, centerZ) > result.key().distanceSquared(centerX, centerZ)) removeVisual(farthest, false);
                if (resident.size() >= config.maxResidentChunks || residentBytes + chunk.estimatedBytes > (long) config.residentMiB << 20) { deferred++; return true; }
            }
            install(result.key(), chunk); restored++;
            maxRestoreNanos = Math.max(maxRestoreNanos, System.nanoTime() - start);
        } catch (Exception e) {
            rejected++; retryAfter.put(result.key(), Long.MAX_VALUE);
            WorldCacheClient.LOGGER.warn("Rejected cached visual chunk {}", result.key(), e);
        }
        return true;
    }
    private ChunkKey farthestResident() {
        ChunkKey result = null;
        long distance = -1;
        for (ChunkKey candidate : resident.keySet()) {
            long current = candidate.distanceSquared(centerX, centerZ);
            if (current > distance) { result = candidate; distance = current; }
        }
        return result;
    }
    private void install(ChunkKey key, VisualChunk chunk) {
        resident.put(key, chunk); residentBytes += chunk.estimatedBytes;
        for (int i = 0; i < chunk.getSections().length; i++) {
            int y = level.getSectionYFromSectionIndex(i);
            level.setSectionDirtyWithNeighbors(key.x(), y, key.z());
        }
        level.onChunkLoaded(new ChunkPos(key.x(), key.z()));
        ChunkTrackerHolder.get(level).onChunkStatusAdded(key.x(), key.z(), 3);
    }
    private void removeVisual(ChunkKey key, boolean replacement) {
        VisualChunk old = resident.remove(key);
        if (old == null) return;
        meshRevision++;
        residentBytes -= old.estimatedBytes;
        if (!replacement) {
            for (int i = 0; i < old.getSections().length; i++) {
                int y = level.getSectionYFromSectionIndex(i);
                level.setSectionDirtyWithNeighbors(key.x(), y, key.z());
            }
        }
        // Drop both flags. The real light update must complete before Sodium builds the replacement.
        ChunkTrackerHolder.get(level).onChunkStatusRemoved(key.x(), key.z(), 3);
    }
    public String status() {
        return String.format(Locale.ROOT, "NWC %s: RAM=%d chunks/~%d MiB, dirty=%d, queues W=%d (%d KiB) R=%d, captured=%d written=%d unchanged=%d restored=%d missing=%d rejected=%d corrupt=%d deferred=%d; max capture/install=%.2f/%.2f ms; overruns=%d; mesh=%d sections/%d KiB, first mesh=%.2f ms; batch=%d/%d radius=%d cap=%d/%d MiB; groups=%d maxGroup=%d",
            io.failed() ? "I/O FAILED" : paused ? "PAUSED" : "active", resident.size(), residentBytes >> 20, dirty.size(), io.queuedWrites(), io.queuedBytes() >> 10, pending.size(), captured, io.written, io.unchanged, restored, missed, rejected, io.corrupt, deferred,
            maxCaptureNanos / 1e6, maxRestoreNanos / 1e6, slowTicks, meshSections, meshBytes >> 10, firstMeshNanos / 1e6,
            maxRestoredPerTick, config.restoresPerTick, radius, config.maxResidentChunks, config.residentMiB, io.readGroups, io.maxReadGroup)
            + (loadingIdle() ? "; leitura=ociosa" : "; leitura=ativa");
    }
    public long restoredCount() { return restored; }
    public int residentCount() { return resident.size(); }
    public long readGroupCount() { return io.readGroups; }
    public long writtenCount() { return io.written; }
    public long meshSectionCount() { return meshSections; }
    public long maxRestoredPerTick() { return maxRestoredPerTick; }
    public boolean loadingIdle() { return loadCursor == null && waitingGroup == null && newlySaved.isEmpty() && !rescanRequested && pending.isEmpty(); }
    public void updateConfig(CacheConfig updated) {
        config = updated;
        io.updateStorageLimits((long) updated.cacheDiskMiB << 20, (long) updated.freeDiskMiB << 20);
        if (centerX != Integer.MIN_VALUE) rebuildCandidates(centerX, centerZ, updated.radiusChunks);
        meshRevision++;
    }
    @Override public void close() {
        closed = true;
        resident.clear();
        pending.clear(); newlySaved.clear(); retryAfter.clear(); io.close();
    }
    /** Only while this level's renderer is alive (live disable or namespace switch). */
    public void detachVisuals() { for (var key : List.copyOf(resident.keySet())) removeVisual(key, false); }
}
