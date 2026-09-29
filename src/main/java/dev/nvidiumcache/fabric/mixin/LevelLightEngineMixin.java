package dev.nvidiumcache.fabric.mixin;

import dev.nvidiumcache.fabric.WorldCacheClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineMixin {
    @Unique private LightChunkGetter nwc$source;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void nwc$owner(LightChunkGetter source, boolean block, boolean sky, CallbackInfo ci) { nwc$source = source; }
    @Inject(method = "lightOnInColumn", at = @At("HEAD"), cancellable = true)
    private void nwc$column(long pos, CallbackInfoReturnable<Boolean> cir) {
        if (nwc$source == null || !(nwc$source.getLevel() instanceof ClientLevel level)) return;
        var session = WorldCacheClient.get(level);
        if (session != null && session.get(SectionPos.x(pos), SectionPos.z(pos)) != null) cir.setReturnValue(true);
    }
}
