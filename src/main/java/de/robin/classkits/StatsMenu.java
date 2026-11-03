package de.robin.classkits;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/** /stats: Oberflaeche mit gelaufenen Bloecken, Kills und Spielzeit. */
public final class StatsMenu {
    private StatsMenu() {
    }

    private static final class ReadOnlyMenu extends ChestMenu {
        ReadOnlyMenu(int id, net.minecraft.world.entity.player.Inventory inv, SimpleContainer c) {
            super(net.minecraft.world.inventory.MenuType.GENERIC_9x3, id, inv, c, 3);
        }

        @Override
        public void clicked(int slotId, int button, ClickType type, Player player) {
            // nichts darf bewegt werden
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack icon(ItemStack stack, String title, ChatFormatting color, String... lines) {
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(title).withStyle(s -> s.withColor(color).withBold(true).withItalic(false)));
        List<Component> lore = new java.util.ArrayList<>();
        for (String l : lines) {
            lore.add(Component.literal(l).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static String time(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h + " h " + m + " min " + s + " s";
    }

    private static void open(ServerPlayer p) {
        long cm = p.getStats().getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM))
                + p.getStats().getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))
                + p.getStats().getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM));
        long kills = p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
        long totalSeconds = p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 20;

        SimpleContainer c = new SimpleContainer(27);
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (int i = 0; i < 27; i++) c.setItem(i, filler.copy());
        c.setItem(11, icon(new ItemStack(Items.DIAMOND_BOOTS), "Gelaufene Blöcke", ChatFormatting.AQUA,
                (cm / 100) + " Blöcke"));
        c.setItem(13, icon(new ItemStack(Items.IRON_SWORD), "Kills insgesamt", ChatFormatting.RED,
                kills + " Spieler-Kills"));
        c.setItem(15, icon(new ItemStack(Items.CLOCK), "Spielzeit", ChatFormatting.GOLD,
                "Gesamt: " + time(totalSeconds),
                "Heute gespielt: " + time(PlayTime.secondsUsed(p.getUUID())),
                "Heute noch übrig: " + time(PlayTime.secondsLeft(p.getUUID()))));

        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ReadOnlyMenu(id, inv, c),
                Component.literal("Statistiken")));
    }

    public static void registerCommand(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("stats").executes(ctx -> {
            open(ctx.getSource().getPlayerOrException());
            return 1;
        }));
    }
}
