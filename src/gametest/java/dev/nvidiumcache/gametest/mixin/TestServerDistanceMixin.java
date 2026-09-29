package dev.nvidiumcache.gametest.mixin;

import dev.nvidiumcache.gametest.PersistenceGameTest;

import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Test-only emulation of a server with a smaller send radius. Never packaged in the release mod. */
@Mixin(IntegratedServer.class)
public abstract class TestServerDistanceMixin {
    @ModifyArg(method = "tickServer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I", ordinal = 0), index = 1)
    private int nwc$testServerDistance(int original) {
        return PersistenceGameTest.serverDistance == 0 ? original : PersistenceGameTest.serverDistance;
    }
}
