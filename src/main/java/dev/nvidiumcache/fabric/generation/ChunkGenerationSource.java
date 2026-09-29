package dev.nvidiumcache.fabric.generation;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.concurrent.CompletableFuture;

/** Server-thread contract. Own tickets until release; completion alone must never touch client state. */
public interface ChunkGenerationSource extends AutoCloseable {
    CompletableFuture<LevelChunk> request(ChunkPos pos);
    void release(ChunkPos pos);
    @Override void close();
}
