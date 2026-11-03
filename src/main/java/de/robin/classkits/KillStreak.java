package de.robin.classkits;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kill-Streak-Partikel hinter dem Spieler (bleiben nur, solange man nicht stirbt):
 * ab 1 Kill gruene Sterne, ab 3 Kills "boese Villager"-Wolken, ab 5 Kills Partikel in der Farbe der Waffe.
 */
public final class KillStreak {
    private KillStreak() {
    }

    /** Spieler mit diesem Tag sehen keine Streak-Partikel (gesetzt durch /partikel off). */
    private static final String OFF_TAG = "classkits_noparticles";

    private static final Map<UUID, Integer> streak = new HashMap<>();
    private static int tickCounter = 0;

    public static void add(ServerPlayer p) {
        int n = streak.merge(p.getUUID(), 1, Integer::sum);
        Cmd.actionbar(serverOf(p), p.getUUID(), "Kill-Streak: " + n, "gold");
    }

    public static void set(UUID id, int value) {
        if (value <= 0) {
            streak.remove(id);
        } else {
            streak.put(id, value);
        }
    }

    public static void reset(UUID id) {
        streak.remove(id);
    }

    private static MinecraftServer serverOf(ServerPlayer p) {
        return p.level().getServer();
    }

    public static void tick(MinecraftServer server) {
        if (++tickCounter % 4 != 0) {
            return;
        }
        for (Map.Entry<UUID, Integer> en : new ArrayList<>(streak.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null || !p.isAlive()) {
                continue;
            }
            int n = en.getValue();
            if (n >= 5) {
                // Ab 5 Kills: drei Toene der Waffenfarbe (dunkel, normal, hell)
                KitStore.Entry e = ClassKitsMod.store().get(p.getUUID());
                for (String d : dusts(e == null ? null : e.kit())) {
                    emit(server, p, d, "0.25 0.4 0.25", 2);
                }
            } else if (n >= 3) {
                emit(server, p, "minecraft:angry_villager", "0.25 0.3 0.25", 1);
            } else {
                emit(server, p, "minecraft:happy_villager", "0.25 0.4 0.25", 3);
            }
        }
    }

    private static void emit(MinecraftServer server, ServerPlayer p, String particle, String delta, int count) {
        Cmd.run(server, "execute as " + p.getUUID() + " at " + p.getUUID()
                + " run execute positioned ^ ^1 ^-0.7 run particle " + particle + " ~ ~ ~ " + delta
                + " 0 " + count + " normal @a[tag=!" + OFF_TAG + "]");
    }

    private static String dust(double r, double g, double b) {
        return "minecraft:dust{color:[" + r + "," + g + "," + b + "],scale:1.0}";
    }

    /** Dolch rot, Block (Schild) blau, Hammer gelb, jeweils dunkel, normal und hell. */
    private static String[] dusts(Kit kit) {
        if (kit == Kit.DAGGER) return new String[] {dust(0.5, 0.0, 0.0), dust(1.0, 0.1, 0.1), dust(1.0, 0.55, 0.55)};
        if (kit == Kit.BLOCK) return new String[] {dust(0.05, 0.15, 0.6), dust(0.2, 0.4, 1.0), dust(0.6, 0.8, 1.0)};
        if (kit == Kit.HAMMER) return new String[] {dust(0.7, 0.55, 0.0), dust(1.0, 0.9, 0.1), dust(1.0, 1.0, 0.6)};
        return new String[] {dust(0.5, 0.5, 0.5), dust(0.8, 0.8, 0.8), dust(1.0, 1.0, 1.0)};
    }

    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("partikel")
                .then(Commands.literal("on").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.removeTag(OFF_TAG);
                    p.sendSystemMessage(Component.literal("Kill-Streak-Partikel sind an.").withStyle(ChatFormatting.GREEN));
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.addTag(OFF_TAG);
                    p.sendSystemMessage(Component.literal("Kill-Streak-Partikel sind aus.").withStyle(ChatFormatting.YELLOW));
                    return 1;
                }))
                // nur Operatoren: /partikel <Name> level <0|1|3|5>
                .then(Commands.argument("spieler", EntityArgument.player())
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("level")
                                .then(Commands.argument("stufe", IntegerArgumentType.integer(0, 5)).executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "spieler");
                                    int stufe = IntegerArgumentType.getInteger(ctx, "stufe");
                                    if (stufe != 0 && stufe != 1 && stufe != 3 && stufe != 5) {
                                        ctx.getSource().sendFailure(Component.literal("Erlaubt sind 0 (aus), 1, 3 und 5."));
                                        return 0;
                                    }
                                    set(target.getUUID(), stufe);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Partikelstufe von "
                                            + target.getName().getString() + " auf " + stufe + " gesetzt."), true);
                                    return 1;
                                })))));
    }
}
