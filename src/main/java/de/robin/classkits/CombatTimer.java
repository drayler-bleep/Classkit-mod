package de.robin.classkits;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Combat-Timer: 1:30 Minuten nach jedem Spieler-Treffer. Ausloggen im Kampf = Tod. */
public final class CombatTimer {
    public static final int DURATION_TICKS = 90 * 20;

    private static final Map<UUID, Integer> timers = new HashMap<>();
    private static final Map<UUID, UUID> lastOpponent = new HashMap<>();

    private CombatTimer() {
    }

    public static boolean inCombat(UUID id) {
        return timers.containsKey(id);
    }

    public static void clear(UUID id) {
        timers.remove(id);
        lastOpponent.remove(id);
    }

    /** Beide Spieler kommen in den Kampf (oder der Timer wird auf 1:30 zurueckgesetzt). */
    public static void tag(ServerPlayer a, ServerPlayer b) {
        enter(a, b);
        enter(b, a);
    }

    private static void enter(ServerPlayer p, ServerPlayer opponent) {
        boolean wasInCombat = timers.containsKey(p.getUUID());
        timers.put(p.getUUID(), DURATION_TICKS);
        lastOpponent.put(p.getUUID(), opponent.getUUID());
        if (!wasInCombat) {
            p.sendSystemMessage(Component.literal("Du bist im Kampf! Wer sich jetzt ausloggt, stirbt.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    public static void tick(MinecraftServer server) {
        for (UUID id : new ArrayList<>(timers.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                timers.remove(id);
                continue;
            }
            int left = timers.get(id) - 1;
            if (left <= 0) {
                clear(id);
                Cmd.actionbar(server, id, "Du bist nicht mehr im Kampf.", "green");
                continue;
            }
            timers.put(id, left);
        }
    }

    /** Verbleibende Kampf-Ticks (0, wenn nicht im Kampf). */
    public static int ticksLeft(UUID id) {
        return timers.getOrDefault(id, 0);
    }

    public static String format(int ticks) {
        int seconds = (ticks + 19) / 20;
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    /** Wird beim Disconnect aufgerufen: Spieler im Kampf stirbt, der Gegner bekommt den Kill. */
    public static void punishLogout(MinecraftServer server, ServerPlayer p) {
        UUID id = p.getUUID();
        UUID oppId = lastOpponent.get(id);
        clear(id);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(p.getName().getString() + " hat sich im Kampf ausgeloggt und ist gestorben!")
                        .withStyle(ChatFormatting.RED), false);
        if (oppId != null) {
            ServerPlayer opp = server.getPlayerList().getPlayer(oppId);
            if (opp != null && opp != p) {
                ClassKitsMod.awardKill(opp);
            }
        }
        ServerLevel level = (ServerLevel) p.level();
        // genericKill umgeht Unverwundbarkeit und Totem
        p.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
    }
}
