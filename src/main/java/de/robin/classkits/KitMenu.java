package de.robin.classkits;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.ChatFormatting;

/**
 * /kit-Menue: Es ist eine normale Kiste (nur serverseitig), alle Klicks werden abgefangen.
 * Man kann nichts herausnehmen. Linksklick auf eine Waffe waehlt die Klasse.
 */
public final class KitMenu extends ChestMenu {
    private static final int BLOCK_SLOT = 10;
    private static final int DAGGER_SLOT = 13;
    private static final int HAMMER_SLOT = 16;

    private final ServerPlayer owner;

    private KitMenu(int containerId, Inventory inventory, Container container, ServerPlayer owner) {
        super(MenuType.GENERIC_9x3, containerId, inventory, container, 3);
        this.owner = owner;
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new KitMenu(id, inv, buildContainer(), player),
                Component.literal("Wähle deine Klasse")));
    }

    private static Container buildContainer() {
        SimpleContainer c = new SimpleContainer(27);
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (int i = 0; i < 27; i++) {
            c.setItem(i, filler.copy());
        }
        c.setItem(BLOCK_SLOT, icon(Kit.BLOCK));
        c.setItem(DAGGER_SLOT, icon(Kit.DAGGER));
        c.setItem(HAMMER_SLOT, icon(Kit.HAMMER));
        return c;
    }

    private static ItemStack icon(Kit kit) {
        ItemStack s = KitItems.create(kit, 1);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(kit.displayName()).withStyle(ChatFormatting.GOLD));
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.literal("Linksklick zum Auswählen").withStyle(ChatFormatting.YELLOW));
        lore.add(Component.literal("Die Wahl gilt dauerhaft.").withStyle(ChatFormatting.GRAY));
        for (int l = 1; l <= KitStore.MAX_LEVEL; l++) {
            lore.add(Component.literal("Level " + l + ": " + kit.unlock(l)).withStyle(ChatFormatting.GRAY));
        }
        s.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        return s;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Nichts darf bewegt werden. Nur Linksklick auf eine Waffe waehlt die Klasse.
        if (clickType == ClickType.PICKUP && button == 0
                && (slotId == BLOCK_SLOT || slotId == DAGGER_SLOT || slotId == HAMMER_SLOT)) {
            Kit kit = slotId == BLOCK_SLOT ? Kit.BLOCK : slotId == DAGGER_SLOT ? Kit.DAGGER : Kit.HAMMER;
            if (ClassKitsMod.select(owner, kit)) {
                owner.closeContainer();
                return;
            }
        }
        this.sendAllDataToRemote(); // Client-Anzeige zuruecksetzen
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
