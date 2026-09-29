package dev.nvidiumcache.fabric.mixin;

import dev.nvidiumcache.fabric.WorldCacheClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientLevel.class, priority = 1100)
public abstract class ClientLevelMixin {
    @Inject(method = "onChunkLoaded", at = @At("RETURN"))
    private void nwc$loaded(ChunkPos pos, CallbackInfo ci) {
        var session = WorldCacheClient.get((ClientLevel) (Object) this);
        if (session != null) session.changed(pos.x(), pos.z());
    }
    @Inject(method = "unload", at = @At("RETURN"))
    private void nwc$unloaded(LevelChunk chunk, CallbackInfo ci) {
        var session = WorldCacheClient.get((ClientLevel) (Object) this);
        if (session != null) session.keepTracked(chunk.getPos().x(), chunk.getPos().z());
    }
}
