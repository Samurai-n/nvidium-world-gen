package dev.nvidiumcache.gametest;

import dev.nvidiumcache.fabric.CacheSession;
import dev.nvidiumcache.fabric.WorldCacheClient;
import me.cortex.nvidium.Nvidium;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Optional regression using a disposable cache copy in this project's build/corpus directory. */
public final class CorpusGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        String input = System.getProperty("nwc.test.corpus");
        if (input == null) return;
        boolean large = Boolean.getBoolean("nwc.test.largeCorpus");
        Path corpus = Path.of(input).toAbsolutePath().normalize();
        Path allowed = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize()
            .getParent().getParent().resolve("corpus").normalize();
        if (!corpus.startsWith(allowed) || !Files.isRegularFile(corpus))
            throw new AssertionError("Corpus must be a disposable file below " + allowed);
        var original = context.computeOnClient(client -> WorldCacheClient.config.copy());
        context.runOnClient(client -> {
            var config = original.copy();
            config.mode = dev.nvidiumcache.fabric.CacheConfig.Mode.EXPLORATION;
            config.radiusChunks = 67; config.maxResidentChunks = 4096; config.residentMiB = 1536;
            config.restoresPerTick = 8; config.backgroundMeshesPerFrame = 4;
            String speed = System.getProperty("nwc.test.speed");
            if (speed != null) config.applyLoadingSpeed(Integer.parseInt(speed));
            WorldCacheClient.config = config;
            client.options.renderDistance().set(8);
            Nvidium.config.region_keep_distance = 257; Nvidium.config.render_fog = false;
        });
        PersistenceGameTest.serverDistance = 2;
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode spectator @p");
            world.getServer().runCommand("tp @p -549 500 420 0 90");
            context.waitFor(client -> WorldCacheClient.get(client.level) != null, 1200);
            context.waitTicks(40);
            context.runOnClient(client -> {
                try {
                    var field = WorldCacheClient.class.getDeclaredField("session");
                    field.setAccessible(true);
                    ((CacheSession) field.get(null)).close();
                    var replacement = CacheSession.open(client.level, corpus);
                    if (replacement == null) throw new AssertionError("No worker slot for corpus");
                    field.set(null, replacement);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            long start = System.nanoTime();
            for (int i = 0; i < 24; i++) {
                if (context.computeOnClient(client -> WorldCacheClient.get(client.level).restoredCount() >= 3500)) break;
                context.waitTicks(100);
                double elapsed = (System.nanoTime() - start) / 1e9;
                context.runOnClient(client -> WorldCacheClient.LOGGER.info("CORPUS at {} seconds: {}", elapsed, WorldCacheClient.get(client.level).status()));
            }
            context.runOnClient(client -> {
                if (WorldCacheClient.get(client.level).restoredCount() < 3500) throw new AssertionError("Corpus did not restore at least 3500 chunks");
            });
            context.waitFor(client -> WorldCacheClient.get(client.level).meshSectionCount() >= 10000, 2400);
            context.waitTicks(40);
            WorldCacheClient.LOGGER.info("CORPUS SCREENSHOT: {}", context.takeScreenshot("nwc-corpus-3500"));
            context.runOnClient(client -> {
                var runtime = Runtime.getRuntime();
                WorldCacheClient.LOGGER.info("CORPUS COMPLETE: {}; heap used={} MiB, max={} MiB", WorldCacheClient.get(client.level).status(),
                    (runtime.totalMemory() - runtime.freeMemory()) >> 20, runtime.maxMemory() >> 20);
            });
            if (large) {
                long beforeMove = context.computeOnClient(client -> WorldCacheClient.get(client.level).restoredCount());
                world.getServer().runCommand("tp @p 1696 500 420 0 90");
                context.waitFor(client -> WorldCacheClient.get(client.level).restoredCount() >= beforeMove + 3000, 2400);
                context.waitTicks(40);
                context.runOnClient(client -> {
                    var cache = WorldCacheClient.get(client.level);
                    var runtime = Runtime.getRuntime();
                    if (cache.residentCount() > WorldCacheClient.config.maxResidentChunks)
                        throw new AssertionError("Large corpus exceeded resident chunk cap");
                    WorldCacheClient.LOGGER.info("LARGE CORPUS AFTER MOVE: {}; heap used={} MiB, max={} MiB", cache.status(),
                        (runtime.totalMemory() - runtime.freeMemory()) >> 20, runtime.maxMemory() >> 20);
                });
            }
        } finally {
            PersistenceGameTest.serverDistance = 0;
            context.runOnClient(client -> WorldCacheClient.config = original);
        }
    }
}
