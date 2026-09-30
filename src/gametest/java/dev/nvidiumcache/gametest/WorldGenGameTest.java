package dev.nvidiumcache.gametest;

import dev.nvidiumcache.fabric.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldGenGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("nwc.test.corpusOnly") || Boolean.getBoolean("nwc.test.configUiOnly") || Boolean.getBoolean("nwc.test.speedBench")) return;
        var original = context.computeOnClient(client -> WorldCacheClient.config.copy());
        AtomicReference<WorldGenerator> generator = new AtomicReference<>();
        context.runOnClient(client -> {
            var settings = original.copy(); settings.enabled = true; settings.mode = CacheConfig.Mode.EXPLORATION;
            settings.radiusChunks = 32; settings.restoresPerTick = 32;
            WorldCacheClient.config = settings;
        });
        PersistenceGameTest.serverDistance = 2;
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> WorldCacheClient.get(client.level) != null, 200);
            context.runOnClient(client -> {
                var dispatcher = net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.getActiveDispatcher();
                var source = (net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource) client.getConnection().getSuggestionsProvider();
                try {
                    dispatcher.execute("nvidium", source);
                    dispatcher.execute("nvidium world cache pause", source);
                    if (!WorldCacheClient.get(client.level).paused) throw new AssertionError("Cache pause command failed");
                    dispatcher.execute("nvidium world cache resume", source);
                    dispatcher.execute("nvidium world gen start 1", source);
                    dispatcher.execute("nvidium world gen status", source);
                    dispatcher.execute("nvidium world gen status debug", source);
                    dispatcher.execute("nvidium world gen start 1", source);
                    dispatcher.execute("nvidium world gen pause", source);
                    if (!WorldCacheClient.generator().paused) throw new AssertionError("Generation pause command failed");
                    dispatcher.execute("nvidium world gen resume", source);
                    dispatcher.execute("nvidium world gen stop", source);
                    if (!WorldCacheClient.generator().stopped) throw new AssertionError("Stop command failed");
                    try { dispatcher.execute("nvidium world gen start 129", source); throw new AssertionError("Invalid radius accepted"); }
                    catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new AssertionError("Command tree failed", e); }
            });
            context.runOnClient(client -> {
                var server = client.getSingleplayerServer();
                var task = new WorldGenerator(server, server.overworld(), WorldCacheClient.get(client.level), 20, 20, 1, 1);
                task.paused = true;
                generator.set(task);
                try {
                    var field = WorldCacheClient.class.getDeclaredField("generator"); field.setAccessible(true); field.set(null, task);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            context.waitTicks(20);
            if (generator.get().savedCount() != 0) throw new AssertionError("Paused job generated chunks");
            generator.get().paused = false;
            context.runOnClient(client -> {
                WorldCacheClient.config.mode = CacheConfig.Mode.WORLD_GEN;
                WorldCacheClient.config.generateWhilePaused = true;
                client.pauseGame(false);
            });
            context.waitFor(client -> client.isPaused(), 100);
            context.waitFor(client -> generator.get().savedCount() == 9, 1200);
            context.runOnClient(client -> {
                client.setScreenAndShow(null);
                WorldCacheClient.config.mode = CacheConfig.Mode.EXPLORATION;
            });
            context.waitFor(client -> WorldCacheClient.get(client.level).get(20, 20) != null, 1200);
            context.waitFor(client -> WorldCacheClient.get(client.level).meshSectionCount() > 0, 1200);
            context.runOnClient(client -> {
                WorldCacheClient.LOGGER.info("WORLD GEN TEST: {}; {}", generator.get().status(), WorldCacheClient.get(client.level).status());
            });
            context.waitFor(client -> WorldCacheClient.get(client.level).loadingIdle(), 1200);
            var cache = context.computeOnClient(client -> WorldCacheClient.get(client.level));
            long reads = cache.readGroupCount();
            context.waitTicks(40);
            if (cache.readGroupCount() != reads) throw new AssertionError("Stationary cache kept reading after its scan completed");
            context.waitTicks(100);
            long scans = cache.backgroundScanColumns();
            context.waitTicks(40);
            if (cache.backgroundScanColumns() != scans) throw new AssertionError("Background mesh scan did not settle while stationary");
            context.runOnClient(client -> {
                var live = WorldCacheClient.editableConfig(); live.radiusChunks = 16;
                WorldCacheClient.saveConfig(live);
                if (WorldCacheClient.get(client.level) != cache) throw new AssertionError("Live distance edit restarted the cache");
                if (WorldCacheClient.config.radiusChunks != 16) throw new AssertionError("Live edit still requires rejoining");
            });
            context.waitFor(client -> cache.get(20, 20) == null, 200);
            context.runOnClient(client -> {
                var live = WorldCacheClient.editableConfig(); live.radiusChunks = 32;
                WorldCacheClient.saveConfig(live);
            });
            context.waitFor(client -> cache.get(20, 20) != null, 1200);
            WorldCacheClient.LOGGER.info("LIVE CACHE TEST: idle reads stable, same-session shrink and expansion passed");
            // Same already-generated area: isolate scheduling overhead, not fresh terrain throughput.
            for (boolean automatic : new boolean[]{false, true}) {
                context.runOnClient(client -> {
                    WorldCacheClient.config.adaptiveGeneration = automatic;
                    generator.get().close();
                    var task = new WorldGenerator(client.getSingleplayerServer(), client.getSingleplayerServer().overworld(), cache, 20, 20, 1, 5);
                    generator.set(task);
                    try {
                        var field = WorldCacheClient.class.getDeclaredField("generator"); field.setAccessible(true); field.set(null, task);
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                });
                long started = System.nanoTime();
                context.waitFor(client -> generator.get().completedCount() == 9, 1200);
                WorldCacheClient.LOGGER.info("SCHEDULER SAMPLE: adaptive={} cachedChunks=9 elapsedMs={}", automatic,
                    (System.nanoTime() - started) / 1_000_000);
            }
            context.runOnClient(client -> {
                var live = WorldCacheClient.editableConfig(); live.enabled = false;
                WorldCacheClient.saveConfig(live);
                if (WorldCacheClient.get(client.level) != null || WorldCacheClient.generator() != null)
                    throw new AssertionError("Live disable did not detach cache and generation");
                live.enabled = true; WorldCacheClient.saveConfig(live);
            });
            context.waitFor(client -> WorldCacheClient.get(client.level) != null && WorldCacheClient.get(client.level).get(20, 20) != null, 1200);
            WorldCacheClient.LOGGER.info("LIVE CACHE TOGGLE TEST: disable/re-enable restored terrain without leaving the world");
            world.getServer().runCommand("gamemode spectator @p");
            for (String dimension : new String[]{"minecraft:the_nether", "minecraft:the_end"}) {
                world.getServer().runCommand("execute in " + dimension + " run tp @p 0 150 0");
                context.waitFor(client -> client.level != null && client.level.dimension().identifier().toString().equals(dimension)
                    && WorldCacheClient.get(client.level) != null, 1200);
                context.runOnClient(client -> {
                    var server = client.getSingleplayerServer();
                    var task = new WorldGenerator(server, server.getLevel(client.level.dimension()), WorldCacheClient.get(client.level), 20, 20, 0, 1);
                    generator.set(task);
                    try {
                        var field = WorldCacheClient.class.getDeclaredField("generator"); field.setAccessible(true); field.set(null, task);
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                });
                context.waitFor(client -> generator.get().savedCount() == 1, 2400);
                context.waitFor(client -> WorldCacheClient.get(client.level).get(20, 20) != null, 1200);
                context.runOnClient(client -> WorldCacheClient.LOGGER.info("WORLD GEN DIMENSION TEST {}: {}; {}", dimension,
                    generator.get().status(), WorldCacheClient.get(client.level).status()));
            }
        } finally {
            if (generator.get() != null) generator.get().close();
            PersistenceGameTest.serverDistance = 0;
            context.runOnClient(client -> WorldCacheClient.saveConfig(original));
        }
    }
}
