package de.robin.classkits;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Verwaltet Teams (Name, Leader, Mitglieder, Einladungen). */
public final class TeamManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("classkits");
    private static final long INVITE_MILLIS = 60_000L;

    public static final class Team {
        public final String name;
        public UUID leader;
        /** UUID -> letzter bekannter Name */
        public final Map<UUID, String> members = new LinkedHashMap<>();

        Team(String name, UUID leader, String leaderName) {
            this.name = name;
            this.leader = leader;
            this.members.put(leader, leaderName);
        }
    }

    private record Invite(String teamKey, String inviterName, long expires) {
    }

    private static final Map<String, Team> teams = new HashMap<>();
    private static final Map<UUID, Team> byPlayer = new HashMap<>();
    private static final Map<UUID, Invite> invites = new HashMap<>();
    private static Path file;

    private TeamManager() {
    }

    public static Team teamOf(UUID id) {
        return byPlayer.get(id);
    }

    public static boolean sameTeam(ServerPlayer a, ServerPlayer b) {
        Team t = byPlayer.get(a.getUUID());
        return t != null && t == byPlayer.get(b.getUUID());
    }

    public static Team find(String name) {
        return teams.get(name.toLowerCase());
    }

    public static boolean validName(String name) {
        return name.matches("[A-Za-z0-9_\\-]{1,16}");
    }

    public static Team create(String name, ServerPlayer leader) {
        Team t = new Team(name, leader.getUUID(), leader.getName().getString());
        teams.put(name.toLowerCase(), t);
        byPlayer.put(leader.getUUID(), t);
        save();
        return t;
    }

    public static void delete(Team t) {
        for (UUID id : t.members.keySet()) {
            byPlayer.remove(id);
        }
        teams.remove(t.name.toLowerCase());
        invites.values().removeIf(i -> i.teamKey().equals(t.name.toLowerCase()));
        save();
    }

    public static void invite(Team t, ServerPlayer target, String inviterName) {
        invites.put(target.getUUID(), new Invite(t.name.toLowerCase(), inviterName, System.currentTimeMillis() + INVITE_MILLIS));
    }

    /** Nimmt die Einladung an. Gibt das Team zurueck oder null, wenn keine (gueltige) Einladung existiert. */
    public static Team accept(ServerPlayer p) {
        Invite inv = invites.remove(p.getUUID());
        if (inv == null || inv.expires() < System.currentTimeMillis()) return null;
        Team t = teams.get(inv.teamKey());
        if (t == null) return null;
        t.members.put(p.getUUID(), p.getName().getString());
        byPlayer.put(p.getUUID(), t);
        save();
        return t;
    }

    public static void remove(Team t, UUID id) {
        t.members.remove(id);
        byPlayer.remove(id);
        save();
    }

    public static void updateName(ServerPlayer p) {
        Team t = byPlayer.get(p.getUUID());
        if (t != null) {
            t.members.put(p.getUUID(), p.getName().getString());
        }
    }

    // --- Speichern / Laden: name|leaderUuid|uuid:name,uuid:name ---

    public static void load(Path path) {
        file = path;
        if (!Files.exists(path)) return;
        try {
            for (String line : Files.readAllLines(path)) {
                String[] p = line.split("\\|");
                if (p.length < 3) continue;
                try {
                    UUID leader = UUID.fromString(p[1]);
                    Team t = new Team(p[0], leader, "?");
                    t.members.clear();
                    for (String m : p[2].split(",")) {
                        String[] kv = m.split(":", 2);
                        if (kv.length == 2) {
                            t.members.put(UUID.fromString(kv[0]), kv[1]);
                        }
                    }
                    if (!t.members.containsKey(leader)) continue;
                    teams.put(p[0].toLowerCase(), t);
                    for (UUID id : t.members.keySet()) {
                        byPlayer.put(id, t);
                    }
                } catch (IllegalArgumentException ignored) {
                    // kaputte Zeile ueberspringen
                }
            }
        } catch (IOException e) {
            LOGGER.error("Konnte Teams nicht laden", e);
        }
    }

    private static void save() {
        if (file == null) return;
        List<String> lines = new ArrayList<>();
        for (Team t : teams.values()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<UUID, String> m : t.members.entrySet()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(m.getKey()).append(':').append(m.getValue());
            }
            lines.add(t.name + "|" + t.leader + "|" + sb);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines);
        } catch (IOException e) {
            LOGGER.error("Konnte Teams nicht speichern", e);
        }
    }
}
