package de.robin.classkits;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** Baut Waffen-Stacks (Name, Lore, Glint) passend zum Level. */
public final class KitItems {
    private KitItems() {
    }

    public static ItemStack create(Kit kit, int level) {
        ItemStack stack = ClassKitsMod.itemOf(kit).getDefaultInstance();
        decorate(stack, kit, level);
        return stack;
    }

    public static void decorate(ItemStack stack, Kit kit, int level) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Level " + level + " / " + KitStore.MAX_LEVEL));
        for (int l = 1; l <= level; l++) {
            lore.add(Component.literal("L" + l + ": " + kit.unlock(l)));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("classkits", kit.modelId(level)));
        if (level >= KitStore.MAX_LEVEL) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true); // maxed
        }
    }

    public static boolean isKitItem(ItemStack s) {
        for (Kit k : Kit.values()) {
            if (s.is(ClassKitsMod.itemOf(k))) return true;
        }
        return false;
    }

    /** Aktualisiert Lore/Glint aller Kit-Waffen im Inventar nach einem Level-Up. */
    public static void refresh(ServerPlayer p, KitStore.Entry e) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            for (Kit k : Kit.values()) {
                if (s.is(ClassKitsMod.itemOf(k))) decorate(s, k, e.kit() == k ? e.level() : 1);
            }
        }
    }

    public static boolean hasKitItem(Inventory inv, Kit kit) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ClassKitsMod.itemOf(kit))) return true;
        }
        return false;
    }
}
