package dev.nvidiumcache.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class StorageBudgetTest {
    @TempDir Path temp;
    @Test void unavailableWorldSaveVolumeFailsClosed() {
        var budget = new StorageBudget(temp, Long.MAX_VALUE, 0, temp.resolve("unavailable-save"));
        assertFalse(budget.check()); assertEquals("storage check failed", budget.reason());
    }
    @Test void countsAllDimensionCachesAndPreservesFilesAtLimit() throws Exception {
        Files.createDirectories(temp.resolve("dimension"));
        var file = temp.resolve("dimension/terrain.sqlite");
        Files.write(file, new byte[16]);
        var budget = new StorageBudget(temp, 64L << 20, 0);
        assertFalse(budget.check()); assertEquals("cache limit", budget.reason());
        assertEquals(16, Files.size(file)); assertEquals(16, budget.usedBytes());
    }
    @Test void lowFreeSpaceStopsWritesWithoutDeletingCache() {
        var budget = new StorageBudget(temp, Long.MAX_VALUE, Long.MAX_VALUE - (64L << 20));
        assertFalse(budget.check()); assertEquals("free space reserve", budget.reason());
    }
    @Test void blockedWorkerRejectsWritesAndClosesWithoutHanging() throws Exception {
        var budget = new StorageBudget(temp, 64L << 20, 0);
        var error = new java.util.concurrent.atomic.AtomicReference<Exception>();
        var worker = CacheWorker.start(temp.resolve("terrain.sqlite"), error::set, (raw, key) -> raw,
            raw -> raw.length, budget);
        assertNotNull(worker);
        try {
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> {
                while (worker.storageStatus().equals("checking")) Thread.sleep(5);
            });
            assertFalse(worker.save(new ChunkKey(0, 0), new byte[]{1}));
            assertFalse(worker.writesAllowed());
        } finally { worker.close(); }
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> { while (!worker.finished()) Thread.sleep(5); });
        assertNull(error.get());
        try (var store = new SnapshotStore(temp.resolve("terrain.sqlite"))) { assertEquals(0, store.count()); }
    }
}
