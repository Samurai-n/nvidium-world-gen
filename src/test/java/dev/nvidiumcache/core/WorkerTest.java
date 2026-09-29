package dev.nvidiumcache.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class WorkerTest {
    @TempDir Path temp;
    @Test void generatedWriteAcknowledgementSurvivesEarlierWriteOfSameKey() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var generatedEntered = new java.util.concurrent.CountDownLatch(1);
        var generatedRelease = new java.util.concurrent.CountDownLatch(1);
        var acknowledged = new java.util.concurrent.CompletableFuture<Boolean>();
        var error = new AtomicReference<Exception>();
        var worker = CacheWorker.start(temp.resolve("ack.sqlite"), error::set);
        assertNotNull(worker);
        var key = new ChunkKey(0, 0);
        try {
            assertTrue(worker.saveDeferred(key, 1, () -> { entered.countDown(); release.await(); return new byte[]{1}; }));
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(worker.saveDeferred(key, 1, () -> {
                generatedEntered.countDown(); generatedRelease.await(); return new byte[]{2};
            }, acknowledged::complete));
            release.countDown();
            assertTrue(generatedEntered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(worker.save(key, new byte[]{3}), "Earlier ordinary completion removed pending acknowledgement");
            generatedRelease.countDown();
            assertTrue(acknowledged.get(5, java.util.concurrent.TimeUnit.SECONDS));
        } finally { release.countDown(); generatedRelease.countDown(); worker.close(); }
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> { while (!worker.finished()) Thread.sleep(5); });
        assertNull(error.get());
        try (var store = new SnapshotStore(temp.resolve("ack.sqlite"))) { assertArrayEquals(new byte[]{2}, store.get(key).orElseThrow()); }
    }
    @Test void prefetchesFullFastUpdateWithoutClientPolling() throws Exception {
        Path file = temp.resolve("prefetch.sqlite");
        try (var store = new SnapshotStore(file)) {
            for (int i = 0; i < 64; i++) store.put(new ChunkKey(i, 0), new byte[]{1});
        }
        AtomicReference<Exception> error = new AtomicReference<>();
        var worker = CacheWorker.start(file, error::set);
        assertNotNull(worker);
        try {
            for (int group = 0; group < 4; group++) {
                var requests = new java.util.ArrayList<CacheWorker.Request>();
                for (int i = group * 16; i < (group + 1) * 16; i++) requests.add(new CacheWorker.Request(new ChunkKey(i, 0), i));
                assertTrue(worker.loadBatch(requests));
            }
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> { while (worker.readGroups < 4) Thread.sleep(5); });
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                for (int i = 0; i < 64; i++) {
                    CacheWorker.Result<byte[]> result;
                    while ((result = worker.poll()) == null) Thread.sleep(5);
                    assertArrayEquals(new byte[]{1}, result.data());
                }
            });
            assertNull(error.get());
        } finally { worker.close(); }
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> { while (!worker.finished()) Thread.sleep(5); });
    }
    @Test void decodingRunsOffCallerAndInvalidRecordDoesNotStopWorker() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> decoderThread = new AtomicReference<>();
        AtomicReference<Exception> workerError = new AtomicReference<>();
        var worker = CacheWorker.start(temp.resolve("prepared.sqlite"), workerError::set, (raw, key) -> {
            decoderThread.set(Thread.currentThread());
            if (raw[0] == 0) throw new java.io.IOException("Invalid test payload");
            return "prepared:" + raw[0];
        }, value -> value.length() * 2L);
        assertNotNull(worker);
        try {
            assertTrue(worker.save(new ChunkKey(0, 0), new byte[]{0}));
            assertTrue(worker.save(new ChunkKey(1, 0), new byte[]{7}));
            assertTrue(worker.load(new ChunkKey(0, 0), 100));
            assertTrue(worker.load(new ChunkKey(1, 0), 101));
            var results = new java.util.ArrayList<CacheWorker.Result<String>>();
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                while (results.size() < 2) {
                    var result = worker.poll();
                    if (result != null) results.add(result); else Thread.sleep(5);
                }
            });
            assertNotSame(caller, decoderThread.get());
            assertEquals(100, results.get(0).token());
            assertNotNull(results.get(0).error());
            assertNull(results.get(0).data());
            assertEquals("prepared:7", results.get(1).data());
            assertNull(results.get(1).error());
            assertFalse(worker.failed());
            assertNull(workerError.get());
        } finally { worker.close(); }
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> { while (!worker.finished()) Thread.sleep(5); });
    }
    @Test void closingDrainsAcceptedWritesAndRejectsNewWork() throws Exception {
        AtomicReference<Exception> error = new AtomicReference<>();
        var worker = CacheWorker.start(temp.resolve("worker.sqlite"), error::set);
        assertNotNull(worker);
        for (int i = 0; i < 100; i++) assertTrue(worker.save(new ChunkKey(1, 1), new byte[]{(byte) i}));
        worker.close();
        assertFalse(worker.save(new ChunkKey(2, 2), new byte[]{0}));
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            while (!worker.finished()) Thread.sleep(5);
        });
        assertNull(error.get());
        try (var store = new SnapshotStore(temp.resolve("worker.sqlite"))) {
            assertArrayEquals(new byte[]{99}, store.get(new ChunkKey(1, 1)).orElseThrow());
            assertEquals(1, store.count());
        }
    }
    @Test void readsCarryTicketsAndShutdownReleasesWorkerSlots() throws Exception {
        AtomicReference<Exception> error = new AtomicReference<>();
        var worker = CacheWorker.start(temp.resolve("reader.sqlite"), error::set);
        assertNotNull(worker);
        try {
            var key = new ChunkKey(-1, -2);
            assertTrue(worker.save(key, new byte[]{1, 2, 3}));
            assertTrue(worker.load(key, 147));
            AtomicReference<CacheWorker.Result<byte[]>> read = new AtomicReference<>();
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                CacheWorker.Result<byte[]> result;
                while ((result = worker.poll()) == null) Thread.sleep(5);
                read.set(result);
            });
            assertEquals(147, read.get().token());
            assertEquals(key, read.get().key());
            assertArrayEquals(new byte[]{1, 2, 3}, read.get().data());
            assertNull(error.get());
        } finally { worker.close(); }
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> { while (!worker.finished()) Thread.sleep(5); });
        var replacement = CacheWorker.start(temp.resolve("other-world.sqlite"), error::set);
        assertNotNull(replacement);
        replacement.close();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> { while (!replacement.finished()) Thread.sleep(5); });
    }
}
