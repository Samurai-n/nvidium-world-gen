package dev.nvidiumcache.fabric.config;

import dev.nvidiumcache.fabric.CacheConfig;
import dev.nvidiumcache.fabric.WorldCacheClient;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** Uses Sodium's public options API, which Reese's also displays. */
public final class SodiumConfigIntegration implements ConfigEntryPoint {
    private static Component text(String key) { return Component.translatable("nwc.config." + key); }
    private static Identifier id(String key) { return Identifier.fromNamespaceAndPath("nvidium_world_cache", key.toLowerCase(java.util.Locale.ROOT)); }

    // Only setters of applied options enter this map. Merge into the latest saved settings,
    // so opening the Cloth screen from here cannot overwrite its changes with stale values.
    private static final class Edits {
        private final LinkedHashMap<String, Consumer<CacheConfig>> changes = new LinkedHashMap<>();
        private CacheConfig.Preset preset = CacheConfig.Preset.CUSTOM;
        private CacheConfig.GenerationPreset generationPreset = CacheConfig.GenerationPreset.CUSTOM;
        void save() {
            if (changes.isEmpty() && preset == CacheConfig.Preset.CUSTOM && generationPreset == CacheConfig.GenerationPreset.CUSTOM) return;
            CacheConfig updated = WorldCacheClient.editableConfig();
            changes.values().forEach(change -> change.accept(updated));
            updated.maxDirtyAgeTicks = Math.max(updated.maxDirtyAgeTicks, updated.debounceTicks);
            updated.applyPreset(preset);
            updated.applyGenerationPreset(generationPreset);
            WorldCacheClient.saveConfig(updated);
            changes.clear(); preset = CacheConfig.Preset.CUSTOM; generationPreset = CacheConfig.GenerationPreset.CUSTOM;
        }
    }

    @Override public void registerConfigLate(ConfigBuilder builder) {
        Edits edits = new Edits();
        StorageEventHandler save = edits::save;
        CacheConfig defaults = new CacheConfig();
        var page = builder.createOptionPage().setName(text("sodium.page"));
        page.addOption(builder.createEnumOption(id("mode"), CacheConfig.Mode.class)
            .setName(text("mode")).setTooltip(text("mode.help")).setDefaultValue(defaults.mode)
            .setElementNameProvider(value -> text("mode." + value.name().toLowerCase(java.util.Locale.ROOT)))
            .setBinding(value -> edits.changes.put("mode", c -> c.mode = value),
                () -> WorldCacheClient.editableConfig().mode).setStorageHandler(save));
        page.addOption(builder.createBooleanOption(id("enabled"))
            .setName(text("enabled")).setTooltip(text("enabled.help"))
            .setDefaultValue(defaults.enabled).setBinding(value -> edits.changes.put("enabled", c -> c.enabled = value),
                () -> WorldCacheClient.editableConfig().enabled).setStorageHandler(save));
        page.addOption(builder.createEnumOption(id("preset"), CacheConfig.Preset.class)
            .setName(text("preset")).setTooltip(text("preset.help"))
            .setDefaultValue(CacheConfig.Preset.CUSTOM)
            .setElementNameProvider(value -> text("preset." + value.name().toLowerCase(java.util.Locale.ROOT)))
            .setBinding(value -> edits.preset = value, () -> CacheConfig.Preset.CUSTOM).setStorageHandler(save)
            // Sodium 0.9.2 keeps displayed values until a screen rebuild. Refresh after
            // storage has applied the preset so its four changed fields are visible now.
            .setApplyHook(state -> ((net.caffeinemc.mods.sodium.client.config.structure.Config) state).resetAllOptionsFromBindings()));
        page.addOption(builder.createEnumOption(id("genPreset"), CacheConfig.GenerationPreset.class)
            .setName(text("genPreset")).setTooltip(text("genPreset.help"))
            .setDefaultValue(CacheConfig.GenerationPreset.CUSTOM)
            .setElementNameProvider(value -> text("genPreset." + value.name().toLowerCase(java.util.Locale.ROOT)))
            .setBinding(value -> edits.generationPreset = value, () -> CacheConfig.GenerationPreset.CUSTOM).setStorageHandler(save)
            .setApplyHook(state -> ((net.caffeinemc.mods.sodium.client.config.structure.Config) state).resetAllOptionsFromBindings()));
        integer(builder, page, edits, save, "radius", 2, 128, defaults.radiusChunks, c -> c.radiusChunks, (c,v) -> c.radiusChunks = v);
        integer(builder, page, edits, save, "genRadius", 1, 128, defaults.generationRadius,
            c -> c.generationRadius, (c,v) -> c.generationRadius = v);
        integer(builder, page, edits, save, "speed", 1, 64, defaults.restoresPerTick, c -> c.restoresPerTick,
            CacheConfig::applyLoadingSpeed);
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            page.addOption(builder.createExternalButtonOption(id("all_settings"))
                .setName(text("sodium.all")).setTooltip(text("sodium.all.help"))
                .setScreenConsumer(parent -> Minecraft.getInstance().setScreenAndShow(CacheConfigScreen.create(parent))));
        }
        builder.registerOwnModOptions().setColorTheme(builder.createColorTheme().setBaseThemeRGB(0x64B66B)).addPage(page);
    }

    private static void integer(ConfigBuilder builder, OptionPageBuilder page, Edits edits, StorageEventHandler save,
            String key, int min, int max, int defaultValue, ToIntFunction<CacheConfig> getter,
            java.util.function.ObjIntConsumer<CacheConfig> setter) {
        page.addOption(builder.createIntegerOption(id(key)).setName(text(key)).setTooltip(text(key + ".help"))
            .setDefaultValue(defaultValue).setRange(min, max, 1)
            .setValueFormatter(value -> Component.literal(key.equals("speed") ? value + " / 64" : Integer.toString(value)))
            .setBinding(value -> edits.changes.put(key, c -> setter.accept(c, value)),
                () -> getter.applyAsInt(WorldCacheClient.editableConfig())).setStorageHandler(save));
    }
}
