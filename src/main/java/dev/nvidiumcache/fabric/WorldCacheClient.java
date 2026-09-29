package dev.nvidiumcache.fabric;

import dev.nvidiumcache.core.PayloadCodec;
import dev.nvidiumcache.core.GenerationFollowPlanner;
import dev.nvidiumcache.fabric.mixin.BiomeManagerAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class WorldCacheClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("nvidium-world-cache");
    public static volatile CacheConfig config;
    private static volatile CacheSession session;
    private static volatile WorldGenerator generator;
    private static GenerationFollowPlanner followPlanner;
    public static WorldGenerator generator() { return generator; }
    /** Only the local server bypasses its pause while a running generation job needs ticks. */
    public static boolean keepLocalServerRunning() {
        var settings = config;
        var task = generator;
        var current = session;
        return settings != null && settings.enabled && settings.mode == CacheConfig.Mode.WORLD_GEN
            && settings.generateWhilePaused && task != null && task.hasActiveWork()
            && current != null && !current.paused;
    }
    public static String startGeneration(int radius) {
        Minecraft client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        if (server == null) return "World Gen requer um mundo local. Em servidores, o cache guarda o terreno recebido.";
        if (session == null || client.player == null || client.level == null) return "Ative o cache e aguarde o mundo carregar.";
        var level = server.getLevel(client.level.dimension());
        if (level == null) return "Dimensão indisponível.";
        if (generator != null) generator.close();
        var pos = client.player.chunkPosition();
        generator = new WorldGenerator(server, level, session, pos.x(), pos.z(), radius, config.generationIntervalTicks);
        followPlanner = new GenerationFollowPlanner(pos.x(), pos.z(), radius);
        return "World Gen iniciado: raio " + radius + " chunks, na dimensão atual. Use /nvidium world gen status.";
    }
    private static ClientLevel lastLevel;
    private static long ticks;
    private static CacheConfig pendingConfig;

    public static CacheConfig editableConfig() { return (pendingConfig == null ? config : pendingConfig).copy(); }

    public static void saveConfig(CacheConfig updated) {
        updated.save();
        pendingConfig = updated.copy();
        applyPendingConfig();
    }

    private static void applyPendingConfig() {
        if (pendingConfig == null) return;
        CacheConfig previous = config;
        config = pendingConfig; pendingConfig = null;
        if (FabricLoader.getInstance().isModLoaded("bobby")) config.enabled = false;
        if (session != null) {
            if (!config.enabled || !java.util.Objects.equals(previous.worldNamespace, config.worldNamespace)) {
                if (Minecraft.getInstance().level == session.level) session.detachVisuals();
                closeSession();
            }
            else session.updateConfig(config);
        }
        if (generator != null) {
            if (config.mode != CacheConfig.Mode.WORLD_GEN && previous.mode != config.mode) {
                generator.close(); generator = null; followPlanner = null;
            } else {
                generator.updateConfig(config);
                if (!generator.stopped && previous.generationRadius != config.generationRadius) startGeneration(config.generationRadius);
            }
        }
    }

    @Override public void onInitializeClient() {
        config = CacheConfig.load();
        if (FabricLoader.getInstance().isModLoaded("bobby")) {
            config.enabled = false;
            LOGGER.error("Bobby detected: World Cache disabled to prevent two competing fake-chunk providers. Use separate comparison profiles.");
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticks++;
            if (client.level != lastLevel) {
                closeSession(); applyPendingConfig(); lastLevel = client.level;
            }
            if (config.enabled && client.level != null && session == null && ticks % 20 == 0) {
                try { session = CacheSession.open(client.level, storagePath(client)); }
                catch (Exception e) { LOGGER.error("Cannot open visual cache", e); config.enabled = false; }
            }
            CacheSession current = session;
            if (current != null) current.tick(client);
            if (current != null && generator == null && config.mode == CacheConfig.Mode.WORLD_GEN
                    && client.player != null && client.getSingleplayerServer() != null) startGeneration(config.generationRadius);
            WorldGenerator task = generator;
            if (current != null && task != null && !task.paused && !task.stopped && config.followPlayer
                    && config.mode == CacheConfig.Mode.WORLD_GEN && client.player != null && client.getSingleplayerServer() != null) {
                if (followPlanner == null) followPlanner = new GenerationFollowPlanner(task.centerX(), task.centerZ(), task.radius());
                var pos = client.player.chunkPosition();
                if (followPlanner.shouldRecenter(pos.x(), pos.z(), ticks) && task.canRecenter()) {
                    LOGGER.info("Recenter World Gen from {},{} to {},{} after player movement",
                        task.centerX(), task.centerZ(), pos.x(), pos.z());
                    startGeneration(task.radius());
                }
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            WorldGenerator current = generator;
            if (current != null) current.tick(server);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CacheSession disconnected = session;
            client.execute(() -> { if (session == disconnected) { closeSession(); applyPendingConfig(); lastLevel = null; } });
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> closeSession());
        WorldCommands.register();
        LOGGER.info("Nvidium World Cache experimental 26.2 adapter initialized; enabled={}", config.enabled);
    }

    public static CacheSession get(ClientLevel level) {
        CacheSession current = session;
        return current != null && current.level == level ? current : null;
    }
    private static int reportStatus(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        var current = session;
        String status = current == null ? "NWC: inactive (check config/log)" : current.status();
        source.sendFeedback(Component.literal(status)); LOGGER.info(status); return 1;
    }
    private static void closeSession() {
        WorldGenerator generating = generator; generator = null;
        followPlanner = null;
        if (generating != null) generating.close();
        CacheSession previous = session;
        session = null;
        if (previous != null) { LOGGER.info(previous.status()); previous.close(); }
    }
    private static Path storagePath(Minecraft client) {
        String world;
        if (client.getSingleplayerServer() != null) {
            var server = client.getSingleplayerServer();
            // Absolute save identity + authoritative seed; never open or modify the save itself.
            world = "local:" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                    + ":" + server.overworld().getSeed();
        } else {
            var server = client.getCurrentServer();
            if (server == null) throw new IllegalStateException("Server identity unavailable; refusing shared unknown-world cache");
            world = "remote:" + server.ip;
        }
        String mods = FabricLoader.getInstance().getAllMods().stream()
                .filter(m -> !m.getMetadata().getId().equals("nvidium_world_cache"))
                .map(m -> m.getMetadata().getId() + "=" + m.getMetadata().getVersion().getFriendlyString()).sorted().reduce("", (a,b) -> a + "\n" + b);
        long biomeSeedHash = ((BiomeManagerAccessor) client.level.getBiomeManager()).nwc$seedHash();
        String identity = "snapshot-v1\n26.2\n" + world + "\n" + biomeSeedHash + "\n" + client.level.dimension().identifier() + "\n" + mods + "\n" + config.worldNamespace;
        String digest = HexFormat.of().formatHex(PayloadCodec.digest(identity.getBytes(StandardCharsets.UTF_8)));
        Path root = FabricLoader.getInstance().getGameDir().resolve(".nvidium-world-cache");
        Path stable = root.resolve(digest).resolve("terrain.sqlite");
        if (java.nio.file.Files.exists(stable)) return stable;
        // Alpha 1 included this mod's own version in world identity. Reuse its exact matching
        // namespace in place, without moving/deleting user data or invalidating it on upgrades.
        String legacyMods = java.util.stream.Stream.concat(mods.lines().filter(line -> !line.isBlank()),
                java.util.stream.Stream.of("nvidium_world_cache=0.1.0-alpha.1+26.2"))
                .sorted().reduce("", (a,b) -> a + "\n" + b);
        String legacyIdentity = "snapshot-v1\n26.2\n" + world + "\n" + biomeSeedHash + "\n" + client.level.dimension().identifier() + "\n" + legacyMods + "\n" + config.worldNamespace;
        String legacyDigest = HexFormat.of().formatHex(PayloadCodec.digest(legacyIdentity.getBytes(StandardCharsets.UTF_8)));
        Path legacy = root.resolve(legacyDigest).resolve("terrain.sqlite");
        if (java.nio.file.Files.exists(legacy)) { LOGGER.info("Reusing matching alpha 1 cache"); return legacy; }
        return stable;
    }
}
