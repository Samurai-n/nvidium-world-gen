package dev.nvidiumcache.fabric;

import dev.nvidiumcache.core.ChunkKey;
import dev.nvidiumcache.core.GenerationPacer;
import dev.nvidiumcache.core.SpatialCursor;
import dev.nvidiumcache.fabric.generation.ChunkGenerationSource;
import dev.nvidiumcache.fabric.generation.VanillaChunkGenerationSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Server-thread scheduler with independently bounded requests, snapshots and cache writes. */
public final class WorldGenerator implements AutoCloseable {
    private static final int INDEX_BATCH = 128;
    private static final long SNAPSHOT_LIMIT = 32L << 20;
    private final ChunkGenerationSource source;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final CacheSession cache;
    private final SpatialCursor cursor;
    private final int total, centerX, centerZ, radius;
    private final GenerationPacer pacer = new GenerationPacer();
    private final Map<ChunkPos, CompletableFuture<LevelChunk>> loading = new LinkedHashMap<>();
    private final Deque<ChunkPos> ready = new ArrayDeque<>();
    private final Deque<ChunkSnapshotCodec.Captured> snapshots = new ArrayDeque<>();
    private final List<Write> writes = new ArrayList<>();
    private List<ChunkPos> checking;
    private CompletableFuture<Set<ChunkKey>> lookup;
    private long retainedBytes;
    private int selected;
    private volatile int pending, queued, interval, maxParallel;
    private volatile boolean adaptive;
    public volatile boolean paused, stopped;
    private volatile int saved, reused, skipped;
    private volatile String state = "starting";

    private record Write(ChunkSnapshotCodec.Captured snapshot, CompletableFuture<Boolean> done) {}

    public WorldGenerator(MinecraftServer server, ServerLevel level, CacheSession cache, int x, int z, int radius, int interval) {
        this.server = server; this.level = level; this.cache = cache; this.interval = interval;
        source = new VanillaChunkGenerationSource(level);
        adaptive = WorldCacheClient.config.adaptiveGeneration;
        maxParallel = Math.max(1, Math.min(16, WorldCacheClient.config.generationParallelTasks));
        centerX = x; centerZ = z; this.radius = radius;
        cursor = new SpatialCursor(x, z, radius); total = (radius * 2 + 1) * (radius * 2 + 1);
    }
    public int savedCount() { return saved; }
    public boolean hasActiveWork() { return !paused && !stopped && !state.equals("complete") && !state.equals("failed"); }
    public int completedCount() { return saved + reused + skipped; }
    public int centerX() { return centerX; }
    public int centerZ() { return centerZ; }
    public int radius() { return radius; }
    public boolean canRecenter() { return !stopped; }
    public void updateConfig(CacheConfig settings) {
        interval = settings.generationIntervalTicks;
        maxParallel = settings.generationParallelTasks;
        adaptive = settings.adaptiveGeneration;
    }
    public String status() {
        String label = switch (state) {
            case "starting" -> "iniciando";
            case "paused" -> "pausado";
            case "complete" -> "concluído";
            case "stopped" -> "interrompido";
            case "failed" -> "parado por erro; consulte o log";
            case "waiting: memory" -> "aguardando memória livre";
            case "waiting: interval" -> "ajustando ritmo";
            default -> state.startsWith("waiting: ") ? "aguardando: " + state.substring(9) : "gerando";
        };
        if (!state.equals("complete") && !stopped && !state.equals("failed")) {
            if (server.isPaused()) label = "jogo pausado";
            else if (pacer.noRecentTick(System.nanoTime())) label = "retomando após pausa";
        }
        return "World Gen: " + label + "; " + completedCount() + " / " + total + " processadas"
            + "; novas=" + saved + ", já no cache=" + reused + ", fora do limite=" + skipped
            + "; pedidos=" + pending + "/" + maxParallel + ", fila=" + queued + "; centro=" + centerX + "," + centerZ
            + "; cadência ~" + Math.round(pacer.tickMillis()) + " ms/tick; intervalo="
            + (adaptive ? pacer.adaptiveInterval(interval) : pacer.interval(interval)) + " ticks";
    }
    public void tick(MinecraftServer current) {
        if (current != server || stopped || state.equals("complete")) return;
        pacer.observe(System.nanoTime());
        try {
            collectWrites();
            collectLookup();
            freezeReady();
            if (!paused && !cache.paused) submitWrites();
            pending = loading.size();
            queued = snapshots.size() + writes.size();
            if (paused || cache.paused) { state = "paused"; return; }
            if (!cache.generationReady()) { state = "waiting: " + cache.storageStatus(); return; }
            if (selected == total && ready.isEmpty() && checking == null && lookup == null && loading.isEmpty()
                    && snapshots.isEmpty() && writes.isEmpty()) { state = "complete"; return; }
            scheduleLookup();
            int parallel = pacer.tickMillis() > 75 ? 1 : maxParallel;
            if (!pacer.ready(interval, adaptive)) { state = "waiting: interval"; return; }
            long deadline = System.nanoTime() + 750_000L;
            var runtime = Runtime.getRuntime();
            for (int count = 0; count < 8 && System.nanoTime() < deadline && loading.size() < parallel && !ready.isEmpty(); count++) {
                long free = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
                if (free < Math.max(256L << 20, runtime.maxMemory() / 10)) { state = "waiting: memory"; break; }
                if (retainedBytes >= SNAPSHOT_LIMIT) break;
                var pos = ready.removeFirst();
                loading.put(pos, source.request(pos));
            }
            pending = loading.size();
            state = "generating";
        } catch (Exception e) {
            stopped = true; state = "failed";
            source.close(); loading.clear(); ready.clear(); snapshots.clear(); writes.clear();
            pending = queued = 0;
            WorldCacheClient.LOGGER.error("World Gen stopped safely near {},{}", centerX, centerZ, e);
        }
    }
    private void collectLookup() {
        if (lookup == null || !lookup.isDone()) return;
        Set<ChunkKey> present = lookup.join();
        for (var pos : checking) {
            if (present.contains(new ChunkKey(pos.x(), pos.z()))) reused++;
            else ready.addLast(pos);
        }
        checking = null; lookup = null;
    }
    private void scheduleLookup() {
        if (lookup != null || ready.size() >= INDEX_BATCH / 2 || selected == total) return;
        if (checking == null) {
            checking = new ArrayList<>(INDEX_BATCH);
            while (selected < total && checking.size() < INDEX_BATCH) {
                var key = cursor.nextInPass(); selected++;
                var pos = new ChunkPos(key.x(), key.z());
                if (level.getWorldBorder().isWithinBounds(pos)) checking.add(pos);
                else skipped++;
            }
            if (checking.isEmpty()) { checking = null; return; }
        }
        lookup = cache.checkCached(checking.stream().map(pos -> new ChunkKey(pos.x(), pos.z())).toList());
        // A full I/O mailbox leaves the staged batch intact for the next tick.
    }
    private void freezeReady() {
        long deadline = System.nanoTime() + 1_500_000L;
        int frozen = 0;
        for (Iterator<Map.Entry<ChunkPos, CompletableFuture<LevelChunk>>> it = loading.entrySet().iterator(); it.hasNext()
                && frozen < 4 && System.nanoTime() < deadline && retainedBytes < SNAPSHOT_LIMIT;) {
            var entry = it.next();
            if (!entry.getValue().isDone()) continue;
            var snapshot = ChunkSnapshotCodec.freeze(entry.getValue().join());
            source.release(entry.getKey()); it.remove();
            retainedBytes += snapshot.retainedBytes();
            snapshots.addLast(snapshot); frozen++;
        }
    }
    private void submitWrites() {
        for (int i = 0; i < 8 && !snapshots.isEmpty() && cache.generationReady(); i++) {
            var snapshot = snapshots.peekFirst();
            var done = new CompletableFuture<Boolean>();
            if (!cache.saveGenerated(snapshot, done::complete)) break;
            snapshots.removeFirst(); writes.add(new Write(snapshot, done));
        }
    }
    private void collectWrites() {
        for (Iterator<Write> it = writes.iterator(); it.hasNext();) {
            var write = it.next();
            if (!write.done().isDone()) continue;
            it.remove();
            if (write.done().join()) { saved++; retainedBytes -= write.snapshot().retainedBytes(); }
            else snapshots.addFirst(write.snapshot());
        }
    }
    @Override public void close() {
        stopped = true; state = "stopped";
        server.execute(() -> {
            source.close(); loading.clear(); ready.clear(); snapshots.clear(); writes.clear();
            checking = null; lookup = null; retainedBytes = 0; pending = queued = 0;
        });
    }
}
