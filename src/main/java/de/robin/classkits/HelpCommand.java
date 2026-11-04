package de.robin.classkits;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /help: zeigt alle Befehle, die ein normaler Spieler nutzen kann. */
public final class HelpCommand {
    private HelpCommand() {
    }

    private static void line(ServerPlayer p, String cmd, String text) {
        p.sendSystemMessage(Component.literal(cmd).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" - " + text).withStyle(ChatFormatting.GRAY)));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("help").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            p.sendSystemMessage(Component.literal("=== Befehle ===").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            line(p, "/kit", "Klassen-Menü öffnen");
            line(p, "/kit choose <block|dolch|hammer>", "Klasse direkt wählen");
            line(p, "/kit info", "Deine Klasse und dein Level");
            line(p, "/team create <Name>", "Team erstellen");
            line(p, "/team invite <Spieler>", "Spieler einladen");
            line(p, "/team accept", "Einladung annehmen");
            line(p, "/team kick <Spieler>", "Spieler aus dem Team werfen");
            line(p, "/team leave", "Team verlassen");
            line(p, "/team info", "Team-Infos");
            line(p, "/team delete", "Team löschen");
            line(p, "/stats", "Statistiken (Blöcke, Kills, Spielzeit)");
            line(p, "/playtime left", "Verbleibende Spielzeit");
            line(p, "/partikel on|off", "Kill-Streak-Partikel für dich ein-/ausschalten");
            line(p, "/cooldown hotbar|upper|middle", "Position der Cooldown-Anzeige");
            line(p, "/knockback on|off", "Rückstoß deiner Waffe (Block, Hammer) ein-/ausschalten");
            line(p, "/help", "Diese Liste");
            return 1;
        }));
    }
}
