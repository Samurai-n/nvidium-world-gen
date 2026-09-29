package dev.nvidiumcache.fabric.mixin;

import dev.nvidiumcache.fabric.config.CacheConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

/** Optional Cloth integration, scoped to screens created by this mod. */
@Pseudo
@Mixin(targets = "me.shedaniel.clothconfig2.gui.AbstractConfigScreen", remap = false)
public abstract class ConfigTooltipMixin {
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"), remap = false)
    private void nwc$belowOption(GuiGraphicsExtractor graphics, Font font, List<FormattedCharSequence> lines, int x, int y) {
        var screen = (net.minecraft.client.gui.screens.Screen) (Object) this;
        if (!CacheConfigScreen.owns(screen)) { graphics.setTooltipForNextFrame(font, lines, x, y); return; }
        StringBuilder text = new StringBuilder();
        for (var line : lines) {
            if (!text.isEmpty()) text.append(' ');
            line.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
        }
        var wrapped = font.split(Component.literal(text.toString()), Math.max(40, Math.min(340, screen.width - 24)));
        graphics.setTooltipForNextFrame(font, wrapped, (width, height, mouseX, mouseY, tipWidth, tipHeight) -> {
            int left = Math.max(8, Math.min(mouseX + 12, width - tipWidth - 8));
            int top = mouseY + 24;
            if (top + tipHeight + 8 > height) top = Math.max(8, mouseY - tipHeight - 24);
            return new Vector2i(left, top);
        }, x, y, false);
    }
}
