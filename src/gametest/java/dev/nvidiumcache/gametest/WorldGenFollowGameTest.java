package dev.nvidiumcache.gametest;

import dev.nvidiumcache.fabric.CacheConfig;
import dev.nvidiumcache.fabric.WorldCacheClient;
import dev.nvidiumcache.fabric.WorldGenerator;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Same-dimension waypoint travel, pause, resume and explicit stop in a disposable world. */
public final class WorldGenFollowGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("nwc.test.corpusOnly") || Boolean.getBoolean("nwc.test.configUiOnly") || Boolean.getBoolean("nwc.test.speedBench")) return;
        CacheConfig original = context.computeOnClient(client -> WorldCacheClient.config.copy());
        context.runOnClient(client -> {
            CacheConfig config = original.copy();
            config.enabled = true; config.mode = CacheConfig.Mode.WORLD_GEN;
            config.followPlayer = true; config.generationRadius = 2; config.generationIntervalTicks = 1;
            config.radiusChunks = 32;
            WorldCacheClient.config = config;
        });
        PersistenceGameTest.serverDistance = 2;
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode spectator @p");
            context.waitFor(client -> WorldCacheClient.generator() != null, 200);
            var origin = context.computeOnClient(client -> new int[]{WorldCacheClient.generator().centerX(), WorldCacheClient.generator().centerZ()});
            int x1 = origin[0] + 20, z1 = origin[1] + 20;
            int x2 = x1 + 20, z2 = z1 + 20;
            int x3 = x2 + 20, z3 = z2 + 20;
            world.getServer().runCommand("tp @p " + (x1 * 16 + .5) + " 150 " + (z1 * 16 + .5));
            context.waitFor(client -> WorldCacheClient.generator() != null && WorldCacheClient.generator().centerX() == x1
                && WorldCacheClient.generator().centerZ() == z1, 1200);
            WorldGenerator first = context.computeOnClient(client -> WorldCacheClient.generator());
            first.paused = true;
            world.getServer().runCommand("tp @p " + (x2 * 16 + .5) + " 150 " + (z2 * 16 + .5));
            context.waitTicks(40);
            if (context.computeOnClient(client -> WorldCacheClient.generator()) != first)
                throw new AssertionError("Paused generation unexpectedly followed the player");
            first.paused = false;
            context.waitFor(client -> WorldCacheClient.generator() != first && WorldCacheClient.generator().centerX() == x2
                && WorldCacheClient.generator().centerZ() == z2, 1200);
            WorldGenerator second = context.computeOnClient(client -> WorldCacheClient.generator());
            second.close();
            world.getServer().runCommand("tp @p " + (x3 * 16 + .5) + " 150 " + (z3 * 16 + .5));
            context.waitTicks(40);
            if (context.computeOnClient(client -> WorldCacheClient.generator()) != second)
                throw new AssertionError("Stopped generation restarted after teleportation");
            WorldCacheClient.LOGGER.info("WORLD GEN FOLLOW TEST: teleport recenter, pause/resume and stop passed; last center {},{}",
                second.centerX(), second.centerZ());
        } finally {
            PersistenceGameTest.serverDistance = 0;
            context.runOnClient(client -> WorldCacheClient.config = original);
        }
    }
}
