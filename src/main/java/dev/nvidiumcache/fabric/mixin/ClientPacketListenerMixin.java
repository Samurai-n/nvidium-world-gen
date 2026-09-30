package dev.nvidiumcache.fabric.mixin;

import dev.nvidiumcache.fabric.WorldCacheClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 1100)
public abstract class ClientPacketListenerMixin {
    @Shadow private ClientLevel level;
    @Inject(method = "handleForgetLevelChunk", at = @At("RETURN"))
    private void nwc$keepVisualChunk(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        var session = WorldCacheClient.get(level);
        if (session != null) session.keepTracked(packet.pos().x, packet.pos().z);
    }
}
