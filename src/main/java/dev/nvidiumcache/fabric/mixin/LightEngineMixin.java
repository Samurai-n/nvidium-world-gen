package dev.nvidiumcache.fabric.mixin;

import dev.nvidiumcache.fabric.WorldCacheClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightEngine.class)
public abstract class LightEngineMixin {
    @Shadow @Final protected LightChunkGetter chunkSource;
    @Unique private DataLayer nwc$light(SectionPos pos) {
        if (!(chunkSource.getLevel() instanceof ClientLevel level)) return null;
        var session = WorldCacheClient.get(level);
        return session == null ? null : session.light((Object) this instanceof SkyLightEngine ? LightLayer.SKY : LightLayer.BLOCK, pos);
    }
    @Inject(method = "getDataLayerData", at = @At("HEAD"), cancellable = true)
    private void nwc$section(SectionPos pos, CallbackInfoReturnable<DataLayer> cir) {
        DataLayer data = nwc$light(pos);
        if (data != null) cir.setReturnValue(data);
    }
    @Inject(method = "getLightValue", at = @At("HEAD"), cancellable = true)
    private void nwc$value(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        DataLayer data = nwc$light(SectionPos.of(pos));
        if (data != null) cir.setReturnValue(data.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15));
    }
}
