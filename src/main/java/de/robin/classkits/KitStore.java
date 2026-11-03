package de.robin.classkits;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Speichert Klasse und Kills pro Spieler (wird in einer Textdatei gesichert). */
public final class KitStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("classkits");
    public static final int MAX_LEVEL = 5;

    public record Entry(Kit kit, int kills) {
        public int level() {
            return Math.min(kills + 1, MAX_LEVEL);
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    /** Pro Spieler: welche Gegner er schon getoetet hat (zaehlen nur einmal fuers Level). */
    private final Map<UUID, java.util.Set<UUID>> killed = new HashMap<>();
    private Path file;

    public void load(Path path) {
        this.file = path;
        if (!Files.exists(path)) return;
        try {
            for (String line : Files.readAllLines(path)) {
                String[] p = line.trim().split(";");
                if (p.length < 3) continue;
                try {
                    UUID id = UUID.fromString(p[0]);
                    entries.put(id, new Entry(Kit.valueOf(p[1]), Integer.parseInt(p[2])));
                    if (p.length >= 4 && !p[3].isBlank()) {
                        java.util.Set<UUID> set = new java.util.HashSet<>();
                        for (String v : p[3].split(",")) set.add(UUID.fromString(v));
                        killed.put(id, set);
                    }
                } catch (IllegalArgumentException ignored) {
                    // kaputte Zeile ueberspringen
                }
            }
        } catch (IOException e) {
            LOGGER.error("Konnte Klassen-Daten nicht laden", e);
        }
    }

    private void save() {
        if (file == null) return;
        List<String> lines = new ArrayList<>();
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            java.util.Set<UUID> k = killed.getOrDefault(e.getKey(), java.util.Set.of());
            StringBuilder sb = new StringBuilder();
            for (UUID v : k) sb.append(sb.length() > 0 ? "," : "").append(v);
            lines.add(e.getKey() + ";" + e.getValue().kit().name() + ";" + e.getValue().kills() + ";" + sb);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines);
        } catch (IOException e) {
            LOGGER.error("Konnte Klassen-Daten nicht speichern", e);
        }
    }

    public Entry get(UUID id) {
        return entries.get(id);
    }

    public boolean choose(UUID id, Kit kit) {
        if (entries.containsKey(id)) return false;
        entries.put(id, new Entry(kit, 0));
        save();
        return true;
    }

    /** Zaehlt einen Spieler-Kill. Gibt true zurueck, wenn dadurch ein neues Level erreicht wurde. */
    public boolean addKill(UUID id) {
        Entry e = entries.get(id);
        if (e == null || e.kills() >= MAX_LEVEL - 1) return false;
        entries.put(id, new Entry(e.kit(), e.kills() + 1));
        save();
        return true;
    }

    /** Wie addKill, aber nur wenn dieser Gegner noch nie von diesem Spieler getoetet wurde. */
    public boolean addKill(UUID id, UUID victim) {
        Entry e = entries.get(id);
        if (e == null) return false;
        if (!killed.computeIfAbsent(id, k -> new java.util.HashSet<>()).add(victim)) return false;
        boolean up = addKill(id);
        if (!up) save();
        return up;
    }

    public void reset(UUID id) {
        killed.remove(id);
        entries.remove(id);
        save();
    }
}
