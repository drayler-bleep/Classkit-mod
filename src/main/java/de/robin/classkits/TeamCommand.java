package de.robin.classkits;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /team create | invite | accept | kick | leave | info | delete */
public final class TeamCommand {
    private TeamCommand() {
    }

    private static void msg(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("team")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String name = StringArgumentType.getString(ctx, "name");
                            if (TeamManager.teamOf(p.getUUID()) != null) {
                                msg(p, "Du bist schon in einem Team.", ChatFormatting.RED);
                                return 0;
                            }
                            if (!TeamManager.validName(name)) {
                                msg(p, "Teamname: 1-16 Zeichen (Buchstaben, Zahlen, _ und -).", ChatFormatting.RED);
                                return 0;
                            }
                            if (TeamManager.find(name) != null) {
                                msg(p, "Dieses Team gibt es schon.", ChatFormatting.RED);
                                return 0;
                            }
                            TeamManager.create(name, p);
                            msg(p, "Team " + name + " erstellt. Lade Spieler mit /team invite <Spieler> ein.", ChatFormatting.GREEN);
                            return 1;
                        })))
                .then(Commands.literal("invite")
                        .then(Commands.argument("spieler", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "spieler");
                            TeamManager.Team t = TeamManager.teamOf(p.getUUID());
                            if (t == null) {
                                msg(p, "Du bist in keinem Team. Erstelle eins mit /team create <Name>.", ChatFormatting.RED);
                                return 0;
                            }
                            if (!t.leader.equals(p.getUUID())) {
                                msg(p, "Nur der Team-Leader darf einladen.", ChatFormatting.RED);
                                return 0;
                            }
                            if (target == p) {
                                msg(p, "Du kannst dich nicht selbst einladen.", ChatFormatting.RED);
                                return 0;
                            }
                            if (TeamManager.teamOf(target.getUUID()) != null) {
                                msg(p, target.getName().getString() + " ist schon in einem Team.", ChatFormatting.RED);
                                return 0;
                            }
                            TeamManager.invite(t, target, p.getName().getString());
                            msg(p, "Einladung an " + target.getName().getString() + " gesendet.", ChatFormatting.GREEN);
                            msg(target, p.getName().getString() + " lädt dich in das Team " + t.name
                                    + " ein. Annehmen mit /team accept (60 Sekunden Zeit).", ChatFormatting.YELLOW);
                            return 1;
                        })))
                .then(Commands.literal("accept").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (TeamManager.teamOf(p.getUUID()) != null) {
                        msg(p, "Du bist schon in einem Team.", ChatFormatting.RED);
                        return 0;
                    }
                    TeamManager.Team t = TeamManager.accept(p);
                    if (t == null) {
                        msg(p, "Du hast keine gültige Einladung.", ChatFormatting.RED);
                        return 0;
                    }
                    MinecraftServer server = ctx.getSource().getServer();
                    for (UUID id : t.members.keySet()) {
                        ServerPlayer m = server.getPlayerList().getPlayer(id);
                        if (m != null) {
                            msg(m, p.getName().getString() + " ist dem Team " + t.name + " beigetreten.", ChatFormatting.GREEN);
                        }
                    }
                    return 1;
                }))
                .then(Commands.literal("kick")
                        .then(Commands.argument("spieler", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    ServerPlayer p = ctx.getSource().getPlayer();
                                    List<String> names = new ArrayList<>();
                                    if (p != null) {
                                        TeamManager.Team t = TeamManager.teamOf(p.getUUID());
                                        if (t != null) names.addAll(t.members.values());
                                    }
                                    return SharedSuggestionProvider.suggest(names, builder);
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(ctx, "spieler");
                                    TeamManager.Team t = TeamManager.teamOf(p.getUUID());
                                    if (t == null) {
                                        msg(p, "Du bist in keinem Team.", ChatFormatting.RED);
                                        return 0;
                                    }
                                    if (!t.leader.equals(p.getUUID())) {
                                        msg(p, "Nur der Team-Leader darf Spieler kicken.", ChatFormatting.RED);
                                        return 0;
                                    }
                                    UUID victim = null;
                                    for (Map.Entry<UUID, String> m : t.members.entrySet()) {
                                        if (m.getValue().equalsIgnoreCase(name)) victim = m.getKey();
                                    }
                                    if (victim == null) {
                                        msg(p, name + " ist nicht in deinem Team.", ChatFormatting.RED);
                                        return 0;
                                    }
                                    if (victim.equals(p.getUUID())) {
                                        msg(p, "Du kannst dich nicht selbst kicken. Nutze /team delete.", ChatFormatting.RED);
                                        return 0;
                                    }
                                    TeamManager.remove(t, victim);
                                    msg(p, name + " wurde aus dem Team geworfen.", ChatFormatting.GREEN);
                                    ServerPlayer kicked = ctx.getSource().getServer().getPlayerList().getPlayer(victim);
                                    if (kicked != null) {
                                        msg(kicked, "Du wurdest aus dem Team " + t.name + " geworfen.", ChatFormatting.RED);
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("leave").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    TeamManager.Team t = TeamManager.teamOf(p.getUUID());
                    if (t == null) {
                        msg(p, "Du bist in keinem Team.", ChatFormatting.RED);
                        return 0;
                    }
                    if (t.leader.equals(p.getUUID())) {
                        msg(p, "Als Leader kannst du das Team nur mit /team delete auflösen.", ChatFormatting.RED);
                        return 0;
                    }
                    TeamManager.remove(t, p.getUUID());
                    msg(p, "Du hast das Team " + t.name + " verlassen.", ChatFormatting.GREEN);
                    return 1;
                }))
                .then(Commands.literal("info").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    TeamManager.Team t = TeamManager.teamOf(p.getUUID());
                    if (t == null) {
                        msg(p, "Du bist in keinem Team.", ChatFormatting.RED);
                        return 0;
                    }
                    MinecraftServer server = ctx.getSource().getServer();
                    msg(p, "Team " + t.name + " (" + t.members.size() + " Mitglieder)", ChatFormatting.GOLD);
                    for (Map.Entry<UUID, String> m : t.members.entrySet()) {
                        boolean online = server.getPlayerList().getPlayer(m.getKey()) != null;
                        String role = m.getKey().equals(t.leader) ? " (Leader)" : "";
                        msg(p, "- " + m.getValue() + role + (online ? " [online]" : " [offline]"),
                                online ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                    }
                    return 1;
                }))
                .then(Commands.literal("delete").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    TeamManager.Team t = TeamManager.teamOf(p.getUUID());
                    if (t == null) {
                        msg(p, "Du bist in keinem Team.", ChatFormatting.RED);
                        return 0;
                    }
                    if (!t.leader.equals(p.getUUID())) {
                        msg(p, "Nur der Team-Leader darf das Team löschen.", ChatFormatting.RED);
                        return 0;
                    }
                    MinecraftServer server = ctx.getSource().getServer();
                    List<UUID> ids = new ArrayList<>(t.members.keySet());
                    TeamManager.delete(t);
                    for (UUID id : ids) {
                        ServerPlayer m = server.getPlayerList().getPlayer(id);
                        if (m != null) {
                            msg(m, "Das Team " + t.name + " wurde gelöscht.", ChatFormatting.RED);
                        }
                    }
                    return 1;
                })));
    }
}
