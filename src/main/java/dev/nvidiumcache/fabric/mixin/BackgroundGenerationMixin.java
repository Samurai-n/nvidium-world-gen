package dev.nvidiumcache.fabric.mixin;

import dev.nvidiumcache.fabric.WorldCacheClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve the client's pause screen but keep the local server ticking for active generation. */
@Mixin(IntegratedServer.class)
public abstract class BackgroundGenerationMixin {
    @Redirect(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isPaused()Z"))
    private boolean nwc$pauseServerOnlyWhenIdle(Minecraft client) {
        return client.isPaused() && !WorldCacheClient.keepLocalServerRunning();
    }
}
