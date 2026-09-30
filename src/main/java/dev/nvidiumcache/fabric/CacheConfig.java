package dev.nvidiumcache.fabric;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;

public final class CacheConfig {
    public CacheConfig copy() {
        var gson = new GsonBuilder().create();
        return gson.fromJson(gson.toJson(this), CacheConfig.class);
    }

    public void save() {
        var path = FabricLoader.getInstance().getConfigDir().resolve("nvidium-world-cache.json");
        try {
            Files.createDirectories(path.getParent());
            var temporary = Files.createTempFile(path.getParent(), "nwc-config-", ".tmp");
            try {
                Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(this));
                try { Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException("Cannot save cache configuration", e); }
    }
    public boolean enabled = true;
    public int radiusChunks = 32;
    public int maxResidentChunks = 1024;
    public int residentMiB = 512;
    public int debounceTicks = 20;
    public int maxDirtyAgeTicks = 200;
    public double tickBudgetMillis = 2.0;
    public int groupSide = 4;
    public enum GroupSize {
        SINGLE(1), TWO(2), FOUR(4), EIGHT(8), SIXTEEN(16);
        public final int side;
        GroupSize(int side) { this.side = side; }
        public static GroupSize from(int side) {
            for (var size : values()) if (size.side == side) return size;
            throw new IllegalArgumentException("Invalid group side");
        }
    }
    public enum Preset { CUSTOM, STABLE, FAST, ULTRA }
    public enum GenerationPreset { CUSTOM, STABLE, FAST, ULTRA }
    public enum Mode { EXPLORATION, WORLD_GEN }
    public Mode mode = Mode.WORLD_GEN;
    public int generationRadius = 16;
    public int generationIntervalTicks = 5;
    public int generationParallelTasks = 2;
    public boolean followPlayer = true;
    public boolean adaptiveGeneration = true;
    public boolean generateWhilePaused = true;
    public int cacheDiskMiB = 8192;
    public int freeDiskMiB = 4096;
    /** Simple graphical control; advanced manual values remain untouched until it is applied. */
    public void applyLoadingSpeed(int speed) {
        if (speed < 1 || speed > 64) throw new IllegalArgumentException("Invalid loading speed");
        restoresPerTick = speed;
        backgroundMeshesPerFrame = Math.max(1, (speed + 3) / 4);
        tickBudgetMillis = speed <= 32 ? 2 : 4;
        groupSide = speed <= 8 ? 2 : speed <= 32 ? 4 : 16;
    }
    public void applyPreset(Preset preset) {
        switch (preset) {
            case CUSTOM -> { }
            case STABLE -> { groupSide = 2; restoresPerTick = 8; backgroundMeshesPerFrame = 4; tickBudgetMillis = 2; }
            case FAST -> { groupSide = 4; restoresPerTick = 32; backgroundMeshesPerFrame = 8; tickBudgetMillis = 2; }
            case ULTRA -> { groupSide = 16; restoresPerTick = 64; backgroundMeshesPerFrame = 16; tickBudgetMillis = 4; }
        }
    }
    public void applyGenerationPreset(GenerationPreset preset) {
        switch (preset) {
            case CUSTOM -> { }
            case STABLE -> { generationIntervalTicks = 8; generationParallelTasks = 2; }
            case FAST -> { generationIntervalTicks = 2; generationParallelTasks = 8; }
            case ULTRA -> { generationIntervalTicks = 1; generationParallelTasks = 16; }
        }
    }
    public int restoresPerTick = 32;
    public int backgroundMeshesPerFrame = 8;
    public String worldNamespace = "default";

    public static CacheConfig load() {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        var path = FabricLoader.getInstance().getConfigDir().resolve("nvidium-world-cache.json");
        try {
            CacheConfig config;
            if (Files.exists(path)) config = gson.fromJson(Files.readString(path), CacheConfig.class);
            else { config = new CacheConfig(); Files.writeString(path, gson.toJson(config)); }
            if (config == null || config.radiusChunks < 2 || config.radiusChunks > 128 || config.maxResidentChunks < 16 || config.maxResidentChunks > 8192
                    || config.residentMiB < 32 || config.residentMiB > 4096 || config.debounceTicks < 1 || config.maxDirtyAgeTicks < config.debounceTicks
                    || config.restoresPerTick < 1 || config.restoresPerTick > 64 || config.backgroundMeshesPerFrame < 1 || config.backgroundMeshesPerFrame > 16
                    || !Double.isFinite(config.tickBudgetMillis) || config.tickBudgetMillis < 0.1 || config.tickBudgetMillis > 10 || config.worldNamespace == null)
                throw new IllegalArgumentException("Invalid cache configuration");
            GroupSize.from(config.groupSide);
            if (config.mode == null || config.generationRadius < 1 || config.generationRadius > 128
                    || config.generationIntervalTicks < 1 || config.generationIntervalTicks > 200
                    || config.generationParallelTasks < 1 || config.generationParallelTasks > 16
                    || config.cacheDiskMiB < 128 || config.cacheDiskMiB > 1048576
                    || config.freeDiskMiB < 512 || config.freeDiskMiB > 1048576)
                throw new IllegalArgumentException("Invalid generation/storage limits");
            return config;
        } catch (Exception e) {
            WorldCacheClient.LOGGER.error("Cache configuration invalid; cache disabled until fixed", e);
            CacheConfig config = new CacheConfig(); config.enabled = false; return config;
        }
    }
}
