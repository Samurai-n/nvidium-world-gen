package dev.nvidiumcache.gametest;

import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.nvidiumcache.fabric.CacheConfig;
import dev.nvidiumcache.fabric.WorldCacheClient;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public final class ConfigScreenGameTest implements FabricClientGameTest {
    private ClothConfigScreen open(ClientGameTestContext context) {
        var result = context.computeOnClient(client -> {
            var provider = FabricLoader.getInstance().getEntrypointContainers("modmenu", ModMenuApi.class).stream()
                .filter(entry -> entry.getProvider().getMetadata().getId().equals("nvidium_world_cache")).findFirst().orElseThrow();
            var screen = (ClothConfigScreen) provider.getEntrypoint().getModConfigScreenFactory().create(new TitleScreen());
            return screen;
        });
        context.setScreen(() -> result);
        return result;
    }
    private IntegerSliderEntry radius(ClothConfigScreen screen) {
        String name = Component.translatable("nwc.config.radius").getString();
        return screen.getCategorizedEntries().values().stream().flatMap(java.util.Collection::stream)
            .filter(entry -> entry.getFieldName().getString().equals(name)).map(IntegerSliderEntry.class::cast).findFirst().orElseThrow();
    }
    @SuppressWarnings("unchecked")
    private EnumListEntry<CacheConfig.Preset> preset(ClothConfigScreen screen) {
        String name = Component.translatable("nwc.config.preset").getString();
        return (EnumListEntry<CacheConfig.Preset>) screen.getCategorizedEntries().values().stream()
            .flatMap(java.util.Collection::stream).filter(entry -> entry.getFieldName().getString().equals(name))
            .findFirst().orElseThrow();
    }
    private void selectUltra(ClothConfigScreen screen) {
        try {
            var field = preset(screen).getClass().getSuperclass().getDeclaredField("index");
            field.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicInteger) field.get(preset(screen))).set(3);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not select Ultra preset", e);
        }
    }
    @Override public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("nwc.test.corpusOnly") || Boolean.getBoolean("nwc.test.worldGenOnly") || Boolean.getBoolean("nwc.test.speedBench")) return;
        CacheConfig original = context.computeOnClient(client -> WorldCacheClient.editableConfig());
        try {
            context.getInput().resizeWindow(854, 480);
            var screen = open(context);
            context.waitTicks(5);
            WorldCacheClient.LOGGER.info("CONFIG SCREEN SCREENSHOT: {}", context.takeScreenshot("nwc-config-screen"));
            var hover = context.computeOnClient(client -> {
                try {
                    Class<?> type = radius(screen).getClass();
                    while (type != null) {
                        try {
                            var field = type.getDeclaredField("bounds"); field.setAccessible(true);
                            Object bounds = field.get(radius(screen));
                            int y = bounds.getClass().getField("y").getInt(bounds);
                            return new int[]{screen.width - 160, y + 10};
                        } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
                    }
                    throw new AssertionError("Entry bounds unavailable");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            context.getInput().setCursorPos(hover[0], hover[1]);
            context.waitTicks(10);
            WorldCacheClient.LOGGER.info("CONFIG TOOLTIP SCREENSHOT: {}", context.takeScreenshot("nwc-tooltip-alpha7"));
            context.runOnClient(client -> radius(screen).setValue(48));
            context.waitTicks(3);
            context.clickScreenButton("gui.cancel");
            context.waitTicks(3);
            context.tryClickScreenButton("text.cloth-config.cancel_discard");
            context.tryClickScreenButton("text.cloth-config.quit_discard");
            context.runOnClient(client -> {
                if (WorldCacheClient.editableConfig().radiusChunks != original.radiusChunks) throw new AssertionError("Cancel mutated config");
            });
            var saved = open(context);
            context.runOnClient(client -> {
                radius(saved).setValue(48);
                selectUltra(saved);
                if (preset(saved).getValue() != CacheConfig.Preset.ULTRA)
                    throw new AssertionError("Could not select Ultra preset");
            });
            context.waitTicks(3);
            context.clickScreenButton("text.cloth-config.save_and_done");
            context.runOnClient(client -> {
                CacheConfig loaded = CacheConfig.load();
                if (loaded.radiusChunks != 48 || WorldCacheClient.editableConfig().radiusChunks != 48)
                    throw new AssertionError("Saved setting did not persist");
                if (loaded.groupSide != 16 || loaded.restoresPerTick != 64 || loaded.backgroundMeshesPerFrame != 16
                        || loaded.tickBudgetMillis != 4.0)
                    throw new AssertionError("Ultra preset did not apply all performance settings");
            });
            var reopened = open(context);
            context.runOnClient(client -> {
                if (radius(reopened).getValue() != 48) throw new AssertionError("Screen did not reload saved setting");
                var debounce = reopened.getCategorizedEntries().values().stream().flatMap(java.util.Collection::stream)
                    .filter(entry -> entry.getFieldName().getString().equals(Component.translatable("nwc.config.debounce").getString()))
                    .map(IntegerSliderEntry.class::cast).findFirst().orElseThrow();
                debounce.setValue(1200);
                boolean error = reopened.getCategorizedEntries().values().stream().flatMap(java.util.Collection::stream)
                    .anyMatch(entry -> entry.getConfigError().isPresent());
                if (!error) throw new AssertionError("Invalid delay combination accepted");
            });
            WorldCacheClient.LOGGER.info("CONFIG SCREEN TEST: cancel, save, reopen and cross-field validation passed");
        } finally {
            context.runOnClient(client -> WorldCacheClient.saveConfig(original));
            context.setScreen(TitleScreen::new);
        }
    }
}
