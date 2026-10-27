package de.robin.classkits;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cooldown-Anzeige. Standard ist ueber der Hotbar (zusammen mit dem Combat-Timer in einer Zeile).
 * Mit /cooldown laesst sie sich nach oben (Bossbar) oder in die Bildschirmmitte (Untertitel) verschieben.
 */
public final class CooldownHud {
    private CooldownHud() {
    }

    private enum Pos { HOTBAR, UPPER, MIDDLE }

    private static final String TAG_UPPER = "classkits_cd_upper";
    private static final String TAG_MIDDLE = "classkits_cd_middle";

    private record Cd(String name, int left, int total) {
    }

    private static int tickCounter = 0;
    private static final Set<UUID> bars = new HashSet<>();

    // ------------------------------------------------------------------ Tick

    public static void tick(MinecraftServer server) {
        if (++tickCounter % 10 != 0) {
            return;
        }
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            update(server, p);
        }
    }

    private static Pos posOf(ServerPlayer p) {
        if (p.getTags().contains(TAG_UPPER)) return Pos.UPPER;
        if (p.getTags().contains(TAG_MIDDLE)) return Pos.MIDDLE;
        return Pos.HOTBAR;
    }

    private static void update(MinecraftServer server, ServerPlayer p) {
        UUID id = p.getUUID();
        List<Cd> cds = cooldowns(p);
        int combat = CombatTimer.ticksLeft(id);
        Pos pos = posOf(p);

        String combatJson = combat > 0
                ? "{\"text\":\"Combat-Timer: " + CombatTimer.format(combat) + "\",\"color\":\"red\"}" : null;
        String cdJson = cds.isEmpty() ? null : cdComponent(cds);

        // Actionbar: Combat-Timer, im Hotbar-Modus zusammen mit den Cooldowns
        List<String> bar = new ArrayList<>();
        if (combatJson != null) bar.add(combatJson);
        if (pos == Pos.HOTBAR && cdJson != null) {
            if (!bar.isEmpty()) bar.add("{\"text\":\"   |   \",\"color\":\"gray\"}");
            bar.add(cdJson);
        }
        if (!bar.isEmpty()) {
            Cmd.run(server, "title " + id + " actionbar [" + String.join(",", bar) + "]");
        }

        // Bossbar (oben)
        String barId = "classkits:cd_" + id;
        if (pos == Pos.UPPER && cdJson != null) {
            Cd longest = cds.get(0);
            for (Cd c : cds) {
                if (c.left() > longest.left()) longest = c;
            }
            Cmd.run(server, "bossbar add " + barId + " {\"text\":\"Cooldown\"}");
            Cmd.run(server, "bossbar set " + barId + " players " + id);
            Cmd.run(server, "bossbar set " + barId + " name " + cdJson);
            Cmd.run(server, "bossbar set " + barId + " color yellow");
            Cmd.run(server, "bossbar set " + barId + " max " + longest.total());
            Cmd.run(server, "bossbar set " + barId + " value " + longest.left());
            Cmd.run(server, "bossbar set " + barId + " visible true");
            bars.add(id);
        } else if (bars.remove(id)) {
            Cmd.run(server, "bossbar remove " + barId);
        }

        // Untertitel (Mitte)
        if (pos == Pos.MIDDLE && cdJson != null) {
            Cmd.run(server, "title " + id + " times 0 25 5");
            Cmd.run(server, "title " + id + " subtitle " + cdJson);
            Cmd.run(server, "title " + id + " title {\"text\":\"\"}");
        }
    }

    private static String cdComponent(List<Cd> cds) {
        List<String> parts = new ArrayList<>();
        for (Cd c : cds) {
            if (!parts.isEmpty()) parts.add("{\"text\":\"  \",\"color\":\"gray\"}");
            int sec = (c.left() + 19) / 20;
            parts.add("{\"text\":\"" + c.name() + ": " + sec + " s\",\"color\":\"yellow\"}");
        }
        return "[" + String.join(",", parts) + "]";
    }

    // ------------------------------------------------------------------ Cooldowns

    private static List<Cd> cooldowns(ServerPlayer p) {
        List<Cd> out = new ArrayList<>();
        KitStore.Entry e = ClassKitsMod.store().get(p.getUUID());
        if (e == null) {
            return out;
        }
        switch (e.kit()) {
            case BLOCK:
                add(out, "Block", vanilla(p, ClassKitsMod.BLOCK, CombatAbilities.BLOCK_COOLDOWN_TICKS),
                        CombatAbilities.BLOCK_COOLDOWN_TICKS);
                break;
            case DAGGER:
                if (e.level() >= 2) {
                    add(out, "Dash", vanilla(p, ClassKitsMod.DAGGER, CombatAbilities.DASH_COOLDOWN_TICKS),
                            CombatAbilities.DASH_COOLDOWN_TICKS);
                }
                break;
            default:
                if (e.level() >= 2) {
                    add(out, "Reparatur", CombatAbilities.repairLeft(p.getUUID()), CombatAbilities.REPAIR_COOLDOWN_TICKS);
                }
                if (e.level() >= 3) {
                    add(out, "Blitz", CombatAbilities.boltLeft(p.getUUID()), CombatAbilities.BOLT_COOLDOWN_TICKS);
                }
                break;
        }
        return out;
    }

    private static void add(List<Cd> out, String name, int left, int total) {
        if (left > 0) {
            out.add(new Cd(name, left, total));
        }
    }

    /** Verbleibende Ticks des normalen Item-Cooldowns (Block, Dolch). */
    private static int vanilla(ServerPlayer p, Item item, int total) {
        float pct = p.getCooldowns().getCooldownPercent(new ItemStack(item), 0.0F);
        return Math.round(pct * total);
    }

    // ------------------------------------------------------------------ Befehl

    private static int setPos(CommandContext<CommandSourceStack> ctx, Pos pos, String note) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        p.removeTag(TAG_UPPER);
        p.removeTag(TAG_MIDDLE);
        if (pos == Pos.UPPER) p.addTag(TAG_UPPER);
        if (pos == Pos.MIDDLE) p.addTag(TAG_MIDDLE);
        MinecraftServer server = p.level().getServer();
        if (bars.remove(p.getUUID())) {
            Cmd.run(server, "bossbar remove classkits:cd_" + p.getUUID());
        }
        String where = pos == Pos.HOTBAR ? "über der Hotbar" : pos == Pos.UPPER ? "oben (Bossbar)" : "in der Bildschirmmitte";
        p.sendSystemMessage(Component.literal("Cooldown-Anzeige: " + where + "." + note).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static final String SIDE_NOTE = " (Links/Rechts ist serverseitig nicht möglich, die Anzeige bleibt zentriert.)";

    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cooldown")
                .then(Commands.literal("hotbar").executes(c -> setPos(c, Pos.HOTBAR, "")))
                .then(Commands.literal("down").executes(c -> setPos(c, Pos.HOTBAR, "")))
                .then(Commands.literal("upper").executes(c -> setPos(c, Pos.UPPER, "")))
                .then(Commands.literal("middle").executes(c -> setPos(c, Pos.MIDDLE, "")));
        for (String side : new String[] {"left", "right"}) {
            root.then(Commands.literal(side)
                    .then(Commands.literal("upper").executes(c -> setPos(c, Pos.UPPER, SIDE_NOTE)))
                    .then(Commands.literal("middle").executes(c -> setPos(c, Pos.MIDDLE, SIDE_NOTE)))
                    .then(Commands.literal("down").executes(c -> setPos(c, Pos.HOTBAR, SIDE_NOTE))));
        }
        dispatcher.register(root);
    }
}
