package dev.nvidiumcache.gametest;

import dev.nvidiumcache.fabric.CacheConfig;
import dev.nvidiumcache.fabric.WorldCacheClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Measures fresh server generation, snapshot persistence and queue pressure together. */
public final class WorldGenSpeedGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("nwc.test.speedBench")) return;
        var original = context.computeOnClient(client -> WorldCacheClient.config.copy());
        context.runOnClient(client -> {
            var settings = original.copy();
            settings.enabled = true; settings.mode = CacheConfig.Mode.EXPLORATION;
            settings.generationParallelTasks = Integer.getInteger("nwc.test.parallel", 16);
            settings.generationIntervalTicks = 1;
            settings.adaptiveGeneration = false; settings.radiusChunks = 32;
            WorldCacheClient.config = settings;
        });
        PersistenceGameTest.serverDistance = 2;
        int radius = Integer.getInteger("nwc.test.radius", 5);
        int total = (radius * 2 + 1) * (radius * 2 + 1);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode spectator @p");
            world.getServer().runCommand("tp @p 1280 160 1280 0 90");
            context.waitFor(client -> WorldCacheClient.get(client.level) != null, 1200);
            context.waitFor(client -> client.player != null && client.player.chunkPosition().x() == 80
                && client.player.chunkPosition().z() == 80, 1200);
            context.runOnClient(client -> WorldCacheClient.startGeneration(radius));
            long start = System.nanoTime();
            for (int sample = 0; sample < 240; sample++) {
                context.waitTicks(10);
                if (sample % 10 == 9) context.runOnClient(client -> WorldCacheClient.LOGGER.info("WORLD GEN SPEED at {} s: {}",
                    (System.nanoTime() - start) / 1e9, WorldCacheClient.generator().status()));
                if (context.computeOnClient(client -> WorldCacheClient.generator().completedCount() >= total)) break;
            }
            context.runOnClient(client -> {
                var job = WorldCacheClient.generator();
                if (job.completedCount() != total || job.savedCount() < total * 4 / 5)
                    throw new AssertionError("Fresh generation incomplete: " + job.status());
                WorldCacheClient.LOGGER.info("WORLD GEN SPEED COMPLETE elapsed={} s; {}",
                    (System.nanoTime() - start) / 1e9, job.status());
            });
        } finally {
            PersistenceGameTest.serverDistance = 0;
            context.runOnClient(client -> WorldCacheClient.config = original);
        }
    }
}
