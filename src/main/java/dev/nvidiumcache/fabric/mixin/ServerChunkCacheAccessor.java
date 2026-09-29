package dev.nvidiumcache.fabric.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.concurrent.CompletableFuture;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {
    // The public getChunkFuture blocks when called on the server thread in 26.2.
    @Invoker("getChunkFutureMainThread")
    CompletableFuture<ChunkResult<ChunkAccess>> nwc$requestChunk(int x, int z, ChunkStatus status, boolean create);
}
