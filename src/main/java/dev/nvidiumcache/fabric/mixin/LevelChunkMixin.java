package dev.nvidiumcache.fabric.mixin;

import dev.nvidiumcache.fabric.WorldCacheClient;
import dev.nvidiumcache.fabric.VisualChunk;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void nwc$changed(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (self instanceof VisualChunk || cir.getReturnValue() == null || !(self.getLevel() instanceof ClientLevel level)) return;
        var session = WorldCacheClient.get(level);
        if (session != null) {
            int x = pos.getX() >> 4, z = pos.getZ() >> 4;
            session.changed(x, z);
            // Light and face visibility may change in adjacent chunks; the normal mesher handles geometry.
            if ((pos.getX() & 15) == 0) session.changed(x - 1, z);
            if ((pos.getX() & 15) == 15) session.changed(x + 1, z);
            if ((pos.getZ() & 15) == 0) session.changed(x, z - 1);
            if ((pos.getZ() & 15) == 15) session.changed(x, z + 1);
        }
    }
}
