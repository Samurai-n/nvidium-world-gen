package dev.nvidiumcache.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public final class WorldCommands {
    private static int say(FabricClientCommandSource source, String text) {
        source.sendFeedback(Component.literal(text)); return 1;
    }
    private static String help() {
        return "/nvidium world cache [status|pause|resume]\n/nvidium world gen [status [debug]|start [raio]|pause|resume|stop]\nO raio é em chunks, de 1 a 128. World Gen gera terreno real e aumenta o save do mundo.";
    }
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(literal("nvidium")
            .executes(c -> say(c.getSource(), help()))
            .then(literal("help").executes(c -> say(c.getSource(), help())))
            .then(literal("world").executes(c -> say(c.getSource(), help()))
                .then(literal("cache").executes(c -> cache(c.getSource(), "status"))
                    .then(literal("status").executes(c -> cache(c.getSource(), "status")))
                    .then(literal("pause").executes(c -> cache(c.getSource(), "pause")))
                    .then(literal("resume").executes(c -> cache(c.getSource(), "resume"))))
                .then(literal("gen").executes(c -> gen(c.getSource(), "status"))
                    .then(literal("status").executes(c -> gen(c.getSource(), "status"))
                        .then(literal("debug").executes(c -> gen(c.getSource(), "debug"))))
                    .then(literal("pause").executes(c -> gen(c.getSource(), "pause")))
                    .then(literal("resume").executes(c -> gen(c.getSource(), "resume")))
                    .then(literal("stop").executes(c -> gen(c.getSource(), "stop")))
                    .then(literal("start").executes(c -> say(c.getSource(), WorldCacheClient.startGeneration(WorldCacheClient.config.generationRadius)))
                        .then(argument("raio", IntegerArgumentType.integer(1, 128))
                            .executes(c -> say(c.getSource(), WorldCacheClient.startGeneration(IntegerArgumentType.getInteger(c, "raio"))))))))));
    }
    private static int cache(FabricClientCommandSource source, String action) {
        var cache = WorldCacheClient.get(source.getClient().level);
        if (cache == null) return say(source, "World Cache inativo: verifique as configurações.");
        if (action.equals("pause")) cache.paused = true;
        if (action.equals("resume")) cache.paused = false;
        return say(source, action.equals("status") ? cache.status() + "; SSD=" + cache.storageStatus()
            : "World Cache " + (cache.paused ? "pausado. O World Gen também aguarda." : "retomado."));
    }
    private static int gen(FabricClientCommandSource source, String action) {
        var gen = WorldCacheClient.generator();
        if (gen == null) return say(source, "World Gen inativo. Use /nvidium world gen start em um mundo local.");
        if (action.equals("resume") && gen.stopped) return say(source, "World Gen interrompido. Use /nvidium world gen start para iniciar uma nova área.");
        if (action.equals("pause")) gen.paused = true;
        if (action.equals("resume")) gen.paused = false;
        if (action.equals("stop")) gen.close();
        if (action.equals("status")) return say(source, gen.summaryStatus());
        if (action.equals("debug")) return say(source, gen.status());
        return say(source, "World Gen: " + switch (action) {
            case "pause" -> "pausado.";
            case "resume" -> "retomado.";
            default -> "interrompido.";
        });
    }
}
