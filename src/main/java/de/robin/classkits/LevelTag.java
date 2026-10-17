package de.robin.classkits;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Zeigt [Klasse Level] vor dem Namen (ueber dem Kopf und in der Tab-Liste) ueber Vanilla-Scoreboard-Teams. */
public final class LevelTag {
    private LevelTag() {
    }

    public static void update(MinecraftServer server, ServerPlayer p) {
        KitStore.Entry e = ClassKitsMod.STORE.get(p.getUUID());
        if (e == null) {
            clear(server, p);
            return;
        }
        String team = "ck_" + e.kit().name().toLowerCase(java.util.Locale.ROOT) + "_" + e.level();
        String text = "[" + e.kit().shortName() + " " + e.level() + "] ";
        Cmd.run(server, "team add " + team);
        Cmd.run(server, "team modify " + team + " prefix {\"text\":\"" + text + "\",\"color\":\""
                + e.kit().color() + "\"}");
        Cmd.run(server, "team join " + team + " " + p.getScoreboardName());
    }

    public static void clear(MinecraftServer server, ServerPlayer p) {
        Cmd.run(server, "team leave " + p.getScoreboardName());
    }
}
