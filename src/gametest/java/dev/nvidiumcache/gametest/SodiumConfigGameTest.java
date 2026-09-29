package dev.nvidiumcache.gametest;

import dev.nvidiumcache.fabric.CacheConfig;
import dev.nvidiumcache.fabric.WorldCacheClient;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

/** Exercise real Sodium option bindings, apply/cancel and their shared storage. */
public final class SodiumConfigGameTest implements FabricClientGameTest {
    @SuppressWarnings("unchecked")
    private static <T> StatefulOption<T> option(String key) {
        return (StatefulOption<T>) ConfigManager.CONFIG.getOption(Identifier.fromNamespaceAndPath("nvidium_world_cache", key));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("nwc.test.corpusOnly") || Boolean.getBoolean("nwc.test.worldGenOnly")) return;
        CacheConfig original = context.computeOnClient(client -> WorldCacheClient.editableConfig());
        try {
            context.getInput().resizeWindow(854, 480);
            var screen = context.computeOnClient(client -> {
                var mod = ConfigManager.CONFIG.getModOptions().stream()
                    .filter(m -> m.configId().equals("nvidium_world_cache")).findFirst().orElseThrow();
                var page = (OptionPage) mod.pages().getFirst();
                return VideoSettingsScreen.createScreen(new TitleScreen(), page);
            });
            context.setScreen(() -> screen);
            context.waitTicks(5);
            context.runOnClient(client -> {
                var mod = ConfigManager.CONFIG.getModOptions().stream()
                    .filter(m -> m.configId().equals("nvidium_world_cache")).findFirst().orElseThrow();
                if (screen instanceof VideoSettingsScreen sodium) sodium.jumpToPage(mod.pages().getFirst());
                else {
                    // Reese's 2.2.3 ignores Sodium's createScreen(page) argument. Select
                    // its matching tab explicitly for screenshot/UI validation only.
                    try {
                        var frame = screen.getClass().getMethod("rso$getTabFrame").invoke(screen);
                        var tabs = (java.util.List<?>) frame.getClass().getMethod("getTabs").invoke(frame);
                        Object target = null;
                        for (var tab : tabs) if (tab.getClass().getMethod("getPage").invoke(tab) == mod.pages().getFirst()) target = tab;
                        check(target != null, "Reese's did not create the World Cache tab");
                        frame.getClass().getMethod("setTab", java.util.Optional.class).invoke(frame, java.util.Optional.of(target));
                        screen.getClass().getMethod("rebuildUI").invoke(screen);
                        frame = screen.getClass().getMethod("rso$getTabFrame").invoke(screen);
                        var selected = (java.util.Optional<?>) frame.getClass().getMethod("getSelectedTab").invoke(frame);
                        check(selected.isPresent() && selected.get().getClass().getMethod("getPage").invoke(selected.get()) == mod.pages().getFirst(),
                            "World Cache page was not selected after rebuilding Reese's UI");
                    } catch (ReflectiveOperationException e) { throw new AssertionError("Could not open Reese's cache tab", e); }
                }
            });
            context.waitTicks(40);
            if (!(screen instanceof VideoSettingsScreen)) {
                context.getInput().setCursorPos(140, 290);
                context.getInput().pressMouse(0);
                context.waitTicks(20);
            }
            WorldCacheClient.LOGGER.info("SODIUM CONFIG SCREEN: {}", context.takeScreenshot("nwc-sodium-alpha6-pt"));
            context.runOnClient(client -> {
                for (String key : new String[]{"enabled", "preset", "radius", "genradius", "speed", "mode"}) {
                    check(option(key) != null, "Missing option " + key);
                    check(!option(key).getTooltip().getString().startsWith("nwc.config."), "Untranslated tooltip " + key);
                }
                SodiumConfigGameTest.<Integer>option("radius").modifyValue(48);
                ConfigManager.CONFIG.resetAllOptionsFromBindings();
                check(WorldCacheClient.editableConfig().radiusChunks == original.radiusChunks, "Cancel changed saved settings");
                SodiumConfigGameTest.<Integer>option("radius").modifyValue(48);
                SodiumConfigGameTest.<CacheConfig.Preset>option("preset").modifyValue(CacheConfig.Preset.ULTRA);
                ConfigManager.CONFIG.applyAllOptions();
                CacheConfig saved = CacheConfig.load();
                check(saved.radiusChunks == 48 && saved.maxResidentChunks == original.maxResidentChunks && saved.residentMiB == original.residentMiB,
                    "Preset changed radius/memory or failed to merge edits");
                check(saved.groupSide == 16 && saved.restoresPerTick == 64 && saved.backgroundMeshesPerFrame == 16 && saved.tickBudgetMillis == 4
                    && saved.generationIntervalTicks == 1 && saved.generationParallelTasks == 16,
                    "Preset was not saved correctly");
                check(SodiumConfigGameTest.<Integer>option("speed").getValidatedValue() == 64, "Preset controls did not refresh");
                check(SodiumConfigGameTest.<CacheConfig.Preset>option("preset").getValidatedValue() == CacheConfig.Preset.CUSTOM,
                    "Preset action did not reset for manual editing");
                SodiumConfigGameTest.<Integer>option("speed").modifyValue(32);
                ConfigManager.CONFIG.applyAllOptions();
                saved = CacheConfig.load();
                check(saved.restoresPerTick == 32 && saved.backgroundMeshesPerFrame == 8 && saved.groupSide == 4
                    && saved.generationIntervalTicks == 5 && saved.generationParallelTasks == 8
                    && saved.tickBudgetMillis == 2 && saved.residentMiB == original.residentMiB, "Speed did not coordinate loading stages");
                // Simulate saving the Cloth screen, then editing a different Sodium field.
                CacheConfig external = WorldCacheClient.editableConfig(); external.worldNamespace = "sodium-cross-screen-test";
                WorldCacheClient.saveConfig(external);
                SodiumConfigGameTest.<Integer>option("radius").modifyValue(40);
                ConfigManager.CONFIG.applyAllOptions();
                check(CacheConfig.load().worldNamespace.equals("sodium-cross-screen-test"), "Stale screen overwrote other settings");
            });
            WorldCacheClient.LOGGER.info("SODIUM CONFIG TEST: simple controls, tooltips, cancel, preset apply/refresh, coordinated speed and shared settings passed");
        } finally {
            context.runOnClient(client -> { WorldCacheClient.saveConfig(original); ConfigManager.CONFIG.resetAllOptionsFromBindings(); });
            context.setScreen(TitleScreen::new);
        }
    }
}
