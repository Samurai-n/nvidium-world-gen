package dev.nvidiumcache.gametest;

import dev.nvidiumcache.fabric.WorldCacheClient;
import dev.nvidiumcache.fabric.VisualChunk;
import me.cortex.nvidium.Nvidium;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Isolated generated test world only. Uses the real client, Sodium and Nvidium. */
public final class PersistenceGameTest implements FabricClientGameTest {
    public static volatile int serverDistance;
    @Override public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("nwc.test.corpusOnly") || Boolean.getBoolean("nwc.test.configUiOnly") || Boolean.getBoolean("nwc.test.worldGenOnly") || Boolean.getBoolean("nwc.test.speedBench")) return;
        context.runOnClient(client -> {
            WorldCacheClient.config.mode = dev.nvidiumcache.fabric.CacheConfig.Mode.EXPLORATION;
            client.options.renderDistance().set(8); WorldCacheClient.config.debounceTicks = 5;
            Nvidium.config.region_keep_distance = 257; Nvidium.config.render_fog = false;
        });
        TestWorldSave save;
        try (var world = context.worldBuilder().create()) {
            save = world.getWorldSave();
            world.getServer().runCommand("gamemode creative @p");
            world.getServer().runCommand("tp @p 0 90 0");
            world.getServer().runCommand("setblock 0 85 0 minecraft:gold_block");
            context.waitFor(client -> client.level != null && WorldCacheClient.get(client.level) != null, 1200);
            context.waitFor(client -> WorldCacheClient.get(client.level).writtenCount() >= 40, 2400);
            // Finish baseline captures before teleporting out of the server's send range.
            context.waitTicks(200);
            context.runOnClient(client -> {
                if (!Nvidium.IS_ENABLED) throw new AssertionError("Nvidium not enabled on test GPU");
                WorldCacheClient.LOGGER.info("GAME TEST BEFORE RESTART: {}", WorldCacheClient.get(client.level).status());
            });
            world.getServer().runCommand("gamemode spectator @p");
            world.getServer().runCommand("tp @p 320 1500 0 0 90");
            context.waitTicks(120);
        }
        context.waitTicks(40);
        serverDistance = 2;
        try (var world = save.open()) {
            world.getServer().runOnServer(server -> server.getPlayerList().setViewDistance(2));
            context.waitFor(client -> client.level != null && WorldCacheClient.get(client.level) != null, 1200);
            for (int i = 0; i < 12 && !context.computeOnClient(client -> WorldCacheClient.get(client.level).meshSectionCount() > 0); i++) {
                context.waitTicks(100);
                context.runOnClient(client -> WorldCacheClient.LOGGER.info("GAME TEST RESTORE PROGRESS: {}", WorldCacheClient.get(client.level).status()));
            }
            context.runOnClient(client -> {
                if (client.player.getY() < 1400) throw new AssertionError("Altitude regression scenario was not active");
                if (WorldCacheClient.get(client.level).restoredCount() == 0) throw new AssertionError("No chunks restored from disk");
                if (WorldCacheClient.get(client.level).meshSectionCount() == 0) throw new AssertionError("Restored chunks did not reach Nvidium");
                if (WorldCacheClient.get(client.level).maxRestoredPerTick() <= 1) throw new AssertionError("Restoration never used a batch");
            });
            context.waitFor(client -> WorldCacheClient.get(client.level).get(0, 0) != null, 2400);
            context.takeScreenshot("nwc-restored-horizon");
            context.runOnClient(client -> {
                Nvidium.config.render_fog = true;
                ((me.cortex.nvidium.sodiumCompat.INvidiumWorldRendererGetter) (Object)
                    net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer.instance()).getRenderer().reloadShaders();
            });
            context.waitTicks(30);
            context.takeScreenshot("nwc-altitude-fog-on");
            context.runOnClient(client -> {
                Nvidium.config.render_fog = false;
                ((me.cortex.nvidium.sodiumCompat.INvidiumWorldRendererGetter) (Object)
                    net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer.instance()).getRenderer().reloadShaders();
            });
            context.waitTicks(30);
            context.takeScreenshot("nwc-altitude-fog-off-reloaded");
            context.runOnClient(client -> WorldCacheClient.LOGGER.info("GAME TEST RESTORED: {}", WorldCacheClient.get(client.level).status()));
            // Authoritative replacement: revisit, edit, then verify the client's actual block state.
            world.getServer().runCommand("tp @p 0 90 0");
            context.waitFor(client -> client.level.getChunkSource().getChunk(0, 0, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null
                && !(client.level.getChunkSource().getChunk(0, 0, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) instanceof VisualChunk), 1200);
            world.getServer().runCommand("setblock 0 85 0 minecraft:diamond_block");
            context.waitFor(client -> client.level.getBlockState(new BlockPos(0,85,0)).is(Blocks.DIAMOND_BLOCK), 1200);
            context.waitTicks(220);
            context.runOnClient(client -> WorldCacheClient.LOGGER.info("GAME TEST AUTHORITATIVE UPDATE: {}", WorldCacheClient.get(client.level).status()));
            world.getServer().runCommand("tp @p 320 1500 0 0 90");
            context.waitTicks(60);
        }
        context.waitTicks(40);
        try (var world = save.open()) {
            context.waitFor(client -> WorldCacheClient.get(client.level) != null && WorldCacheClient.get(client.level).get(0, 0) != null, 2400);
            context.runOnClient(client -> {
                if (!WorldCacheClient.get(client.level).get(0, 0).getBlockState(new BlockPos(0, 85, 0)).is(Blocks.DIAMOND_BLOCK))
                    throw new AssertionError("Persisted edit did not survive second reopen");
                WorldCacheClient.LOGGER.info("GAME TEST SECOND REOPEN: {}", WorldCacheClient.get(client.level).status());
            });
        } finally { serverDistance = 0; }
    }
}
