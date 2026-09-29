package dev.nvidiumcache.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.ticks.LevelChunkTicks;

/** Render-only data: never ticked, never promoted to authoritative state or written into a world save. */
public final class VisualChunk extends LevelChunk {
    public final DataLayer[] blockLight, skyLight;
    public final long estimatedBytes;
    public final long installedNanos = System.nanoTime();
    public VisualChunk(Level level, ChunkPos pos, LevelChunkSection[] sections, DataLayer[] block, DataLayer[] sky, int rawBytes) {
        super(level, pos, UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, sections, null, null);
        blockLight = block; skyLight = sky;
        // Deliberately conservative accounting, not a JVM heap measurement.
        // Account for per-section objects without charging a full dense block array to air sections.
        // Raw snapshot size includes packed palettes and light. The multiplier leaves headroom for
        // decoded containers; this is still an estimate, not total JVM or Sodium/GPU memory.
        estimatedBytes = 65536L + Math.max(rawBytes * 4L, sections.length * 4096L);
    }
    @Override public BlockState setBlockState(BlockPos pos, BlockState state, int flags) { return null; }
}
