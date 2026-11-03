package de.robin.classkits;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Der allererste Spieler-Kill auf dem Server wird gross im Chat verkuendet (nur einmal). */
public final class FirstBlood {
    private static boolean done;
    private static Path file;

    private FirstBlood() {
    }

    public static void load(Path path) {
        file = path;
        done = Files.exists(path);
    }

    public static void trigger(MinecraftServer server, ServerPlayer attacker, ServerPlayer victim, DamageSource source) {
        if (done) return;
        done = true;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "done");
        } catch (IOException ignored) {
            // nicht schlimm, dann gibt es es nach einem Neustart eventuell nochmal
        }
        Component msg;
        if (source != null) {
            msg = source.getLocalizedDeathMessage(victim);
        } else {
            msg = Component.literal(victim.getName().getString() + " wurde von "
                    + attacker.getName().getString() + " getötet");
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("FIRST BLOOD! ").append(msg).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Cmd.at(server, p.getUUID(),
                    "playsound minecraft:entity.ender_dragon.growl master @s ~ ~ ~ 1 0.8",
                    "playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1 1",
                    "playsound minecraft:entity.wither.spawn master @s ~ ~ ~ 0.7 1");
        }
    }
}
