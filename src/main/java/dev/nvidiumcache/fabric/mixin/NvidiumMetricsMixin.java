package dev.nvidiumcache.fabric.mixin;

import dev.nvidiumcache.fabric.WorldCacheClient;
import me.cortex.nvidium.managers.SectionManager;
import me.cortex.nvidium.sodiumCompat.IRepackagedResult;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observation only; no replacement of meshes, allocator state or upload code. */
@Mixin(value = SectionManager.class, remap = false)
public abstract class NvidiumMetricsMixin {
    @Inject(method = "uploadChunkBuildResult", at = @At("RETURN"))
    private void nwc$uploaded(ChunkBuildOutput output, CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var session = WorldCacheClient.get(level);
        var packed = ((IRepackagedResult) output).getOutput();
        if (session != null && packed != null) session.uploaded(output.section.getChunkX(), output.section.getChunkZ(), (int) packed.geometry().getLength());
    }
}
