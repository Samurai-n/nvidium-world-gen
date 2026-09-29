package dev.nvidiumcache.fabric.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

public final class ModMenuIntegration implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FabricLoader.getInstance().isModLoaded("cloth-config") ? CacheConfigScreen::create : parent -> null;
    }
}
