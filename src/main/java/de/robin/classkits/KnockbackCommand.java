package de.robin.classkits;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /knockback on|off: schaltet den Rueckstoss deiner Waffe (Block-Punch, Hammer-Schockwelle) fuer dich aus. */
public final class KnockbackCommand {
    private KnockbackCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("knockback")
                .then(Commands.literal("on").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.removeTag(CombatAbilities.NO_KNOCKBACK_TAG);
                    p.sendSystemMessage(Component.literal("Knockback deiner Waffe ist an.").withStyle(ChatFormatting.GREEN));
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.addTag(CombatAbilities.NO_KNOCKBACK_TAG);
                    p.sendSystemMessage(Component.literal("Knockback deiner Waffe ist aus.").withStyle(ChatFormatting.YELLOW));
                    return 1;
                })));
    }
}
