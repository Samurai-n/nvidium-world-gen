package dev.nvidiumcache.fabric.mixin;

import dev.nvidiumcache.core.ChunkKey;
import dev.nvidiumcache.core.SpatialCursor;
import dev.nvidiumcache.fabric.WorldCacheClient;
import me.cortex.nvidium.Nvidium;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateTypes;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.UploadResourceBudget;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supply off-screen terrain to the existing mesher, after its visible-work budget.
 * No GPU state restoration, extra executor or unbounded list of sections.
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class BackgroundMeshingMixin {
    @Shadow @Final private Long2ReferenceMap<RenderSection> sectionByPosition;
    @Shadow @Final private ClientLevel level;
    @Shadow protected abstract void submitSectionTask(ChunkJobCollector collector, RenderSection section, int update, UploadResourceBudget budget, boolean immediate);
    @Unique private SpatialCursor nwc$cursor;
    @Unique private ChunkKey nwc$column;
    @Unique private int nwc$x, nwc$z, nwc$radius, nwc$section;
    @Unique private long nwc$revision = -1;
    @Unique private boolean nwc$idle, nwc$submittedInPass;
    @Unique private dev.nvidiumcache.fabric.CacheSession nwc$session;

    @Inject(method = "submitSectionTasks(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/estimation/UploadResourceBudget;)V", at = @At("RETURN"))
    private void nwc$backgroundMeshes(ChunkJobCollector immediate, ChunkJobCollector deferred, ChunkJobCollector collector, UploadResourceBudget budget, CallbackInfo ci) {
        var session = WorldCacheClient.get(level);
        var player = Minecraft.getInstance().player;
        if (!Nvidium.IS_ENABLED || session == null || session.paused || player == null) return;
        if (session != nwc$session) { nwc$session = session; nwc$cursor = null; nwc$idle = false; }
        int x = player.chunkPosition().x, z = player.chunkPosition().z;
        int radius = WorldCacheClient.config.radiusChunks;
        if (nwc$idle && x == nwc$x && z == nwc$z && radius == nwc$radius && nwc$revision == session.meshRevision()) return;
        if (nwc$cursor == null || nwc$idle || x != nwc$x || z != nwc$z || radius != nwc$radius) {
            nwc$x = x; nwc$z = z; nwc$radius = radius;
            nwc$cursor = new SpatialCursor(x, z, radius); nwc$column = null;
            nwc$revision = session.meshRevision(); nwc$idle = false; nwc$submittedInPass = false;
        }
        long deadline = System.nanoTime() + 500_000L;
        int submitted = 0;
        for (int scanned = 0; scanned < 1024 && submitted < WorldCacheClient.config.backgroundMeshesPerFrame && System.nanoTime() < deadline
                && collector.hasBudgetRemaining() && budget.isAvailable(); scanned++) {
            if (nwc$column == null) {
                nwc$column = nwc$cursor.nextInPass(); nwc$section = level.getMinSectionY();
                if (nwc$column == null) {
                    if (nwc$submittedInPass || nwc$revision != session.meshRevision()) nwc$cursor = null;
                    else nwc$idle = true;
                    break;
                }
                session.backgroundColumnScanned();
                // Skip a missing column once instead of looking up all of its vertical sections.
                if (level.getChunkSource().getChunk(nwc$column.x(), nwc$column.z(),
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) {
                    nwc$column = null; continue;
                }
            }
            var section = sectionByPosition.get(SectionPos.asLong(nwc$column.x(), nwc$section, nwc$column.z()));
            if (++nwc$section > level.getMaxSectionY()) nwc$column = null;
            if (section == null || section.isDisposed()) continue;
            int update = section.getPendingUpdate();
            if (ChunkUpdateTypes.isInitialBuild(update) || ChunkUpdateTypes.isRebuild(update)) {
                submitSectionTask(collector, section, update, budget, false);
                submitted++;
                nwc$submittedInPass = true;
            }
        }
    }
}
