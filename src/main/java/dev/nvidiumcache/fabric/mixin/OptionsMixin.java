package dev.nvidiumcache.fabric.mixin;

import dev.nvidiumcache.fabric.WorldCacheClient;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The server send radius must not clip locally cached terrain. Does not change server simulation. */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Shadow @Final private OptionInstance<Integer> renderDistance;
    @Inject(method = "getEffectiveRenderDistance", at = @At("HEAD"), cancellable = true)
    private void nwc$visualDistance(CallbackInfoReturnable<Integer> cir) {
        if (WorldCacheClient.config != null && WorldCacheClient.config.enabled)
            cir.setReturnValue(Math.max(renderDistance.get(), WorldCacheClient.config.radiusChunks));
    }
}
