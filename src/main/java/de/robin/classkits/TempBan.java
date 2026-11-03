package de.robin.classkits;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

/** Eigene Zeitbans (z. B. 24 h fuer Teamkills). Wird in einer Textdatei gespeichert und laeuft nach Uhrzeit ab. */
public final class TempBan {
    private static final Logger LOGGER = LoggerFactory.getLogger("classkits");
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private record Ban(String name, long until) {
    }

    private static final Map<UUID, Ban> bans = new HashMap<>();
    private static Path file;

    private TempBan() {
    }

    public static void load(Path path) {
        file = path;
        if (!Files.exists(path)) return;
        try {
            for (String line : Files.readAllLines(path)) {
                String[] p = line.trim().split(";");
                if (p.length < 3) continue;
                try {
                    bans.put(UUID.fromString(p[0]), new Ban(p[1], Long.parseLong(p[2])));
                } catch (IllegalArgumentException ignored) {
                    // kaputte Zeile
                }
            }
        } catch (IOException e) {
            LOGGER.error("Konnte Bans nicht laden", e);
        }
    }

    private static void save() {
        if (file == null) return;
        List<String> lines = new ArrayList<>();
        for (Map.Entry<UUID, Ban> e : bans.entrySet()) {
            lines.add(e.getKey() + ";" + e.getValue().name() + ";" + e.getValue().until());
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines);
        } catch (IOException e) {
            LOGGER.error("Konnte Bans nicht speichern", e);
        }
    }

    /** Restzeit in Millisekunden, 0 wenn nicht gebannt. */
    public static long remaining(UUID id) {
        Ban b = bans.get(id);
        if (b == null) return 0;
        long left = b.until() - System.currentTimeMillis();
        if (left <= 0) {
            bans.remove(id);
            save();
            return 0;
        }
        return left;
    }

    public static Component message(long leftMillis) {
        long minutes = (leftMillis + 59_999) / 60_000;
        long h = minutes / 60;
        long m = minutes % 60;
        return Component.literal("Du bist gebannt, weil du ein Teammitglied getötet hast.\nNoch " + h + " h " + m + " min.")
                .withStyle(ChatFormatting.RED);
    }

    /** Bannt den Spieler fuer 24 Stunden und wirft ihn raus. */
    public static void banDay(ServerPlayer p) {
        long until = System.currentTimeMillis() + DAY_MILLIS;
        bans.put(p.getUUID(), new Ban(p.getName().getString(), until));
        save();
        p.connection.disconnect(message(DAY_MILLIS));
    }

    public static void registerCommand(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kitunban").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    UUID found = null;
                    for (Map.Entry<UUID, Ban> e : bans.entrySet()) {
                        if (e.getValue().name().equalsIgnoreCase(name)) found = e.getKey();
                    }
                    if (found == null) {
                        ctx.getSource().sendFailure(Component.literal("Kein Bann für " + name + " gefunden."));
                        return 0;
                    }
                    bans.remove(found);
                    save();
                    ctx.getSource().sendSuccess(() -> Component.literal(name + " wurde entbannt."), true);
                    return 1;
                })));
    }
}
