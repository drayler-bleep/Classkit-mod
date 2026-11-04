package de.robin.classkits;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Spielzeit-Budget: Mo-Fr zusammen 3 Stunden, Sa+So zusammen 1 Stunde (Europe/Berlin). Danach gesperrt bis zur naechsten Phase. */
public final class PlayTime {
    private static final Logger LOGGER = LoggerFactory.getLogger("classkits");
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final int WEEKDAY_LIMIT = 3 * 60 * 60;
    private static final int WEEKEND_LIMIT = 60 * 60;

    private static boolean weekend() {
        java.time.DayOfWeek d = LocalDate.now(ZONE).getDayOfWeek();
        return d == java.time.DayOfWeek.SATURDAY || d == java.time.DayOfWeek.SUNDAY;
    }

    private static int limit() {
        return weekend() ? WEEKEND_LIMIT : WEEKDAY_LIMIT;
    }

    /** Schluessel der aktuellen Phase: Werkwoche (Montag) oder Wochenende (Samstag). */
    private static String periodKey() {
        LocalDate today = LocalDate.now(ZONE);
        if (weekend()) {
            return "E" + (today.getDayOfWeek() == java.time.DayOfWeek.SUNDAY ? today.minusDays(1) : today);
        }
        return "W" + today.minusDays(today.getDayOfWeek().getValue() - 1L);
    }

    private static final Map<UUID, Integer> used = new HashMap<>();
    private static final Map<UUID, String> names = new HashMap<>();
    private static String day = "";
    private static Path file;
    private static int counter = 0;

    private PlayTime() {
    }

    public static void load(Path path) {
        file = path;
        if (!Files.exists(path)) return;
        try {
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty()) return;
            day = lines.get(0).trim();
            for (int i = 1; i < lines.size(); i++) {
                String[] p = lines.get(i).trim().split(";");
                if (p.length < 3) continue;
                try {
                    UUID id = UUID.fromString(p[0]);
                    names.put(id, p[1]);
                    used.put(id, Integer.parseInt(p[2]));
                } catch (IllegalArgumentException ignored) {
                    // kaputte Zeile
                }
            }
        } catch (IOException e) {
            LOGGER.error("Konnte Spielzeit nicht laden", e);
        }
    }

    public static void save() {
        if (file == null) return;
        List<String> lines = new ArrayList<>();
        lines.add(day);
        for (Map.Entry<UUID, Integer> e : used.entrySet()) {
            lines.add(e.getKey() + ";" + names.getOrDefault(e.getKey(), "?") + ";" + e.getValue());
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines);
        } catch (IOException e) {
            LOGGER.error("Konnte Spielzeit nicht speichern", e);
        }
    }

    private static void checkDay() {
        String today = periodKey();
        if (!today.equals(day)) {
            day = today;
            used.clear();
            save();
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlayTime::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> save());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
    }

    /** Gibt true zurueck, wenn der Spieler heute keine Zeit mehr hat (und wirft ihn raus). */
    public static boolean blockOnJoin(ServerPlayer p) {
        checkDay();
        names.put(p.getUUID(), p.getName().getString());
        if (used.getOrDefault(p.getUUID(), 0) >= limit()) {
            p.connection.disconnect(limitMessage());
            return true;
        }
        return false;
    }

    private static Component limitMessage() {
        String text = weekend()
                ? "Deine Spielzeit für dieses Wochenende (1 Stunde) ist aufgebraucht.\nDu kannst ab Montag 0 Uhr wieder spielen."
                : "Deine Spielzeit für diese Werkwoche (3 Stunden) ist aufgebraucht.\nDu kannst ab Samstag 0 Uhr wieder spielen.";
        return Component.literal(text)
                .withStyle(ChatFormatting.RED);
    }

    private static void tick(MinecraftServer server) {
        if (++counter % 20 != 0) return;
        checkDay();
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            UUID id = p.getUUID();
            names.put(id, p.getName().getString());
            int u = used.merge(id, 1, Integer::sum);
            int left = limit() - u;
            if (left <= 0) {
                save();
                p.connection.disconnect(limitMessage());
            } else if (left == 600 || left == 300 || left == 60 || left == 10) {
                String t = left >= 60 ? (left / 60) + " Minuten" : left + " Sekunden";
                p.sendSystemMessage(Component.literal("Noch " + t + " Spielzeit für " + (weekend() ? "dieses Wochenende." : "diese Werkwoche.")).withStyle(ChatFormatting.YELLOW));
            }
        }
        if (counter % 1200 == 0) save();
    }

    /** Verbleibende Sekunden heute. */
    public static int secondsLeft(UUID id) {
        checkDay();
        return Math.max(0, limit() - used.getOrDefault(id, 0));
    }

    public static int secondsUsed(UUID id) {
        checkDay();
        return used.getOrDefault(id, 0);
    }

    private static int showLeft(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int left = secondsLeft(p.getUUID());
        p.sendSystemMessage(Component.literal("Verbleibende Spielzeit (" + (weekend() ? "Wochenende" : "Werkwoche") + "): " + (left / 60) + " min "
                + (left % 60) + " s").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    public static void registerCommand(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("playtime")
                .executes(PlayTime::showLeft)
                .then(Commands.literal("left").executes(PlayTime::showLeft)));
        d.register(Commands.literal("spielzeit")
                .executes(PlayTime::showLeft)
                .then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            UUID found = null;
                            for (Map.Entry<UUID, String> e : names.entrySet()) {
                                if (e.getValue().equalsIgnoreCase(name)) found = e.getKey();
                            }
                            if (found == null) {
                                ctx.getSource().sendFailure(Component.literal("Spieler nicht gefunden: " + name));
                                return 0;
                            }
                            used.remove(found);
                            save();
                            ctx.getSource().sendSuccess(() -> Component.literal("Spielzeit zurückgesetzt: " + name), true);
                            return 1;
                        }))));
    }
}
