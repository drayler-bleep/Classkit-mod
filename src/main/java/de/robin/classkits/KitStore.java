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
    private Path file;

    public void load(Path path) {
        this.file = path;
        if (!Files.exists(path)) return;
        try {
            for (String line : Files.readAllLines(path)) {
                String[] p = line.trim().split(";");
                if (p.length < 3) continue;
                try {
                    entries.put(UUID.fromString(p[0]), new Entry(Kit.valueOf(p[1]), Integer.parseInt(p[2])));
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
            lines.add(e.getKey() + ";" + e.getValue().kit().name() + ";" + e.getValue().kills());
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

    public void reset(UUID id) {
        entries.remove(id);
        save();
    }
}
