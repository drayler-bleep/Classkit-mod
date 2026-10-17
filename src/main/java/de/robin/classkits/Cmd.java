package de.robin.classkits;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Kleine Helfer, die Sounds, Partikel, Titel und Actionbar ueber Vanilla-Befehle ausgeben. */
public final class Cmd {
    private Cmd() {
    }

    public static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    /** Fuehrt die Befehle an der Position des Spielers aus. */
    public static void at(MinecraftServer server, UUID id, String... commands) {
        for (String c : commands) {
            run(server, "execute as " + id + " at " + id + " run " + c);
        }
    }

    public static void actionbar(MinecraftServer server, UUID id, String text, String color) {
        run(server, "title " + id + " actionbar {\"text\":\"" + text + "\",\"color\":\"" + color + "\"}");
    }

    /** Grosser Text in der Bildschirmmitte. */
    public static void title(MinecraftServer server, UUID id, String text, String color) {
        run(server, "title " + id + " times 5 40 15");
        run(server, "title " + id + " title {\"text\":\"" + text + "\",\"color\":\"" + color + "\",\"bold\":true}");
    }
}
