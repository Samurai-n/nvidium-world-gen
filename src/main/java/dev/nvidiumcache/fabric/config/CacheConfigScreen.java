package dev.nvidiumcache.fabric.config;

import dev.nvidiumcache.fabric.CacheConfig;
import dev.nvidiumcache.fabric.WorldCacheClient;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Optional;

public final class CacheConfigScreen {
    private static final java.util.Set<Screen> SCREENS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static boolean owns(Screen screen) { return SCREENS.contains(screen); }
    private static Component text(String key) { return Component.translatable("nwc.config." + key); }

    public static Screen create(Screen parent) {
        CacheConfig draft = WorldCacheClient.editableConfig();
        CacheConfig defaults = new CacheConfig();
        var builder = ConfigBuilder.create().setParentScreen(parent).setTitle(text("title"));
        var category = builder.getOrCreateCategory(text("general"));
        var entries = builder.entryBuilder();
        category.addEntry(entries.startEnumSelector(text("mode"), CacheConfig.Mode.class, draft.mode)
            .setEnumNameProvider(value -> text("mode." + value.name().toLowerCase(java.util.Locale.ROOT)))
            .setDefaultValue(defaults.mode).setTooltip(text("mode.help")).setSaveConsumer(value -> draft.mode = value).build());
        var preset = entries.startEnumSelector(text("preset"), CacheConfig.Preset.class, CacheConfig.Preset.CUSTOM)
            .setEnumNameProvider(value -> text("preset." + value.name().toLowerCase(java.util.Locale.ROOT)))
            .setTooltip(text("preset.help")).build();
        category.addEntry(preset);
        category.addEntry(entries.startEnumSelector(text("group"), CacheConfig.GroupSize.class, CacheConfig.GroupSize.from(draft.groupSide))
            .setEnumNameProvider(raw -> {
                CacheConfig.GroupSize value = (CacheConfig.GroupSize) raw;
                return value.side == 1 ? text("group.single") : Component.literal(value.side + "x" + value.side);
            })
            .setDefaultValue(CacheConfig.GroupSize.FOUR).setTooltip(text("group.help"))
            .setSaveConsumer(value -> draft.groupSide = value.side).build());
        category.addEntry(entries.startTextDescription(text("reopen")).build());
        category.addEntry(entries.startBooleanToggle(text("enabled"), draft.enabled)
            .setTooltip(text("enabled.help")).setDefaultValue(defaults.enabled).setSaveConsumer(value -> draft.enabled = value).build());
        category.addEntry(entries.startIntSlider(text("radius"), draft.radiusChunks, 2, 128)
            .setTooltip(text("radius.help")).setDefaultValue(defaults.radiusChunks).setSaveConsumer(value -> draft.radiusChunks = value).build());
        category.addEntry(entries.startIntField(text("chunks"), draft.maxResidentChunks)
            .setTooltip(text("chunks.help")).setMin(16).setMax(8192).setDefaultValue(defaults.maxResidentChunks).setSaveConsumer(value -> draft.maxResidentChunks = value).build());
        category.addEntry(entries.startIntSlider(text("memory"), draft.residentMiB, 32, 4096)
            .setTooltip(text("memory.help")).setDefaultValue(defaults.residentMiB).setSaveConsumer(value -> draft.residentMiB = value).build());
        category.addEntry(entries.startIntSlider(text("restoreBatch"), draft.restoresPerTick, 1, 64)
            .setTooltip(text("restoreBatch.help")).setDefaultValue(defaults.restoresPerTick)
            .setSaveConsumer(value -> draft.restoresPerTick = value).build());
        category.addEntry(entries.startIntSlider(text("meshBatch"), draft.backgroundMeshesPerFrame, 1, 16)
            .setTooltip(text("meshBatch.help")).setDefaultValue(defaults.backgroundMeshesPerFrame)
            .setSaveConsumer(value -> draft.backgroundMeshesPerFrame = value).build());
        var debounce = entries.startIntSlider(text("debounce"), draft.debounceTicks, 1, 1200)
            .setTooltip(text("debounce.help")).setDefaultValue(defaults.debounceTicks).setSaveConsumer(value -> draft.debounceTicks = value).build();
        category.addEntry(debounce);
        category.addEntry(entries.startIntField(text("age"), draft.maxDirtyAgeTicks)
            .setTooltip(text("age.help")).setMin(1).setMax(72000).setDefaultValue(defaults.maxDirtyAgeTicks)
            .setErrorSupplier(value -> value < debounce.getValue() ? Optional.of(text("age.error")) : Optional.empty())
            .setSaveConsumer(value -> draft.maxDirtyAgeTicks = value).build());
        category.addEntry(entries.startDoubleField(text("budget"), draft.tickBudgetMillis)
            .setMin(0.1).setMax(10.0).setDefaultValue(defaults.tickBudgetMillis)
            .setErrorSupplier(value -> !Double.isFinite(value) ? Optional.of(text("budget.error")) : Optional.empty())
            .setTooltip(text("budget.help")).setSaveConsumer(value -> draft.tickBudgetMillis = value).build());
        category.addEntry(entries.startStrField(text("namespace"), draft.worldNamespace)
            .setDefaultValue(defaults.worldNamespace).setTooltip(text("namespace.help"))
            .setErrorSupplier(value -> value.isBlank() || value.length() > 128 ? Optional.of(text("namespace.error")) : Optional.empty())
            .setSaveConsumer(value -> draft.worldNamespace = value).build());
        var generation = builder.getOrCreateCategory(text("generation"));
        var generationPreset = entries.startEnumSelector(text("genPreset"), CacheConfig.GenerationPreset.class, CacheConfig.GenerationPreset.CUSTOM)
            .setEnumNameProvider(value -> text("genPreset." + value.name().toLowerCase(java.util.Locale.ROOT)))
            .setTooltip(text("genPreset.help")).build();
        generation.addEntry(generationPreset);
        generation.addEntry(entries.startBooleanToggle(text("generateWhilePaused"), draft.generateWhilePaused)
            .setTooltip(text("generateWhilePaused.help")).setDefaultValue(defaults.generateWhilePaused)
            .setSaveConsumer(value -> draft.generateWhilePaused = value).build());
        generation.addEntry(entries.startBooleanToggle(text("adaptiveGeneration"), draft.adaptiveGeneration)
            .setTooltip(text("adaptiveGeneration.help")).setDefaultValue(defaults.adaptiveGeneration)
            .setSaveConsumer(value -> draft.adaptiveGeneration = value).build());
        generation.addEntry(entries.startBooleanToggle(text("followPlayer"), draft.followPlayer)
            .setTooltip(text("followPlayer.help")).setDefaultValue(defaults.followPlayer)
            .setSaveConsumer(value -> draft.followPlayer = value).build());
        generation.addEntry(entries.startIntSlider(text("genRadius"), draft.generationRadius, 1, 128)
            .setDefaultValue(defaults.generationRadius).setTooltip(text("genRadius.help"))
            .setSaveConsumer(value -> draft.generationRadius = value).build());
        generation.addEntry(entries.startIntSlider(text("genInterval"), draft.generationIntervalTicks, 1, 200)
            .setDefaultValue(defaults.generationIntervalTicks).setTooltip(text("genInterval.help"))
            .setSaveConsumer(value -> draft.generationIntervalTicks = value).build());
        generation.addEntry(entries.startIntSlider(text("genParallel"), draft.generationParallelTasks, 1, 16)
            .setDefaultValue(defaults.generationParallelTasks).setTooltip(text("genParallel.help"))
            .setSaveConsumer(value -> draft.generationParallelTasks = value).build());
        var storage = builder.getOrCreateCategory(text("storage"));
        storage.addEntry(entries.startIntField(text("diskLimit"), draft.cacheDiskMiB)
            .setMin(128).setMax(1048576).setDefaultValue(defaults.cacheDiskMiB).setTooltip(text("diskLimit.help"))
            .setSaveConsumer(value -> draft.cacheDiskMiB = value).build());
        storage.addEntry(entries.startIntField(text("diskReserve"), draft.freeDiskMiB)
            .setMin(512).setMax(1048576).setDefaultValue(defaults.freeDiskMiB).setTooltip(text("diskReserve.help"))
            .setSaveConsumer(value -> draft.freeDiskMiB = value).build());
        builder.setSavingRunnable(() -> { draft.applyPreset(preset.getValue()); draft.applyGenerationPreset(generationPreset.getValue()); WorldCacheClient.saveConfig(draft); });
        Screen screen = builder.build(); SCREENS.add(screen); return screen;
    }
}
