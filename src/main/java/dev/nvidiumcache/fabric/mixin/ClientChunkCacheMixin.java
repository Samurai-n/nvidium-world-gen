package dev.nvidiumcache.fabric.mixin;

import dev.nvidiumcache.fabric.WorldCacheClient;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
    @Shadow @Final private ClientLevel level;
    @Shadow @Final private LevelChunk emptyChunk;
    @Shadow public abstract LevelChunk getChunk(int x, int z, ChunkStatus status, boolean orEmpty);

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("RETURN"), cancellable = true)
    private void nwc$fallback(int x, int z, ChunkStatus status, boolean orEmpty, CallbackInfoReturnable<LevelChunk> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue() != emptyChunk) return;
        var session = WorldCacheClient.get(level);
        if (session != null) {
            var visual = session.get(x, z);
            if (visual != null) cir.setReturnValue(visual);
        }
    }
    @Inject(method = "replaceWithPacketData", at = @At("HEAD"))
    private void nwc$authoritative(int x, int z, FriendlyByteBuf buffer, Map<Heightmap.Types, long[]> heights, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> entities, CallbackInfoReturnable<LevelChunk> cir) {
        var session = WorldCacheClient.get(level);
        if (session != null) session.authoritative(x, z);
    }
    @Inject(method = "drop", at = @At("HEAD"))
    private void nwc$captureBeforeDrop(ChunkPos pos, CallbackInfo ci) {
        var session = WorldCacheClient.get(level);
        if (session != null) session.beforeDrop(getChunk(pos.x, pos.z, ChunkStatus.FULL, false));
    }
    @Inject(method = "onLightUpdate", at = @At("RETURN"))
    private void nwc$lightChanged(LightLayer type, SectionPos pos, CallbackInfo ci) {
        var session = WorldCacheClient.get(level);
        if (session != null) session.changed(pos.getX(), pos.getZ());
    }
}
