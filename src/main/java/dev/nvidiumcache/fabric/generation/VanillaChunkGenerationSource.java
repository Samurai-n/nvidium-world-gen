package dev.nvidiumcache.fabric.generation;

import dev.nvidiumcache.fabric.mixin.ServerChunkCacheAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Uses the normal server pipeline; acceleration mods can replace that pipeline independently. */
public final class VanillaChunkGenerationSource implements ChunkGenerationSource {
    private final ServerLevel level;
    private final TicketType type = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);
    private final Set<ChunkPos> held = new HashSet<>();
    public VanillaChunkGenerationSource(ServerLevel level) { this.level = level; }
    public CompletableFuture<LevelChunk> request(ChunkPos pos) {
        var source = level.getChunkSource();
        held.add(pos);
        source.addTicketWithRadius(type, pos, 0);
        try {
            return ((ServerChunkCacheAccessor) source).nwc$requestChunk(pos.x(), pos.z(), ChunkStatus.FULL, true)
                .thenApply(result -> {
                    if (!(result.orElse(null) instanceof LevelChunk chunk)) throw new IllegalStateException("Full chunk unavailable at " + pos);
                    return chunk;
                });
        } catch (RuntimeException e) { release(pos); throw e; }
    }
    public void release(ChunkPos pos) {
        if (held.remove(pos)) level.getChunkSource().removeTicketWithRadius(type, pos, 0);
    }
    public void close() { for (var pos : Set.copyOf(held)) release(pos); }
}
