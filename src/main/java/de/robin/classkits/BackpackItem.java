package de.robin.classkits;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Rucksack mit 27 Slots. Rechtsklick oeffnet ihn, der Inhalt wird im Item selbst gespeichert. */
public final class BackpackItem extends Item {
    public static final Item BACKPACK = ClassKitsMod.register("backpack",
            new BackpackItem(ClassKitsMod.itemProperties("backpack", "Rucksack")));

    public BackpackItem(Item.Properties properties) {
        super(properties);
    }

    private static int counter = 0;

    /** Maximal ein Rucksack pro Spieler: Extras werden abgeworfen und koennen nicht aufgehoben werden. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (++counter % 5 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            net.minecraft.world.entity.player.Inventory inv = p.getInventory();
            int found = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (!(st.getItem() instanceof BackpackItem)) continue;
                found++;
                if (found > 1) {
                    inv.setItem(i, ItemStack.EMPTY);
                    net.minecraft.world.entity.item.ItemEntity ie = p.drop(st, false);
                    if (ie != null) ie.setPickUpDelay(40);
                    p.sendSystemMessage(Component.literal("Du kannst nur einen Rucksack im Inventar haben.")
                            .withStyle(net.minecraft.ChatFormatting.RED));
                }
            }
            if (found >= 1) {
                // liegende Rucksaecke in der Naehe nicht aufheben lassen
                for (net.minecraft.world.entity.item.ItemEntity ie : p.level().getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(3.0))) {
                    if (ie.getItem().getItem() instanceof BackpackItem) {
                        ie.setPickUpDelay(20);
                    }
                }
            }
        }
    }

    /** Erzwingt das Laden der Klasse (und damit die Registrierung). */
    public static void init() {
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        BackpackContainer container = new BackpackContainer(stack, hand);
        sp.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> ChestMenu.threeRows(id, inv, container),
                Component.literal("Rucksack")));
        return InteractionResult.SUCCESS;
    }

    /** 27 Slots, die sich live in das Rucksack-Item zurueckschreiben. */
    private static final class BackpackContainer extends SimpleContainer {
        private final ItemStack backpack;
        private final InteractionHand hand;
        private boolean loading = true;

        BackpackContainer(ItemStack backpack, InteractionHand hand) {
            super(27);
            this.backpack = backpack;
            this.hand = hand;
            ItemContainerContents contents = backpack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            NonNullList<ItemStack> tmp = NonNullList.withSize(27, ItemStack.EMPTY);
            contents.copyInto(tmp);
            for (int i = 0; i < 27; i++) {
                setItem(i, tmp.get(i));
            }
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (loading) return;
            List<ItemStack> list = new ArrayList<>();
            for (int i = 0; i < 27; i++) {
                list.add(getItem(i).copy());
            }
            backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(list));
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            // kein Rucksack im Rucksack, keine Kit-Waffen
            return !(stack.getItem() instanceof BackpackItem) && !KitItems.isKitItem(stack);
        }

        @Override
        public boolean stillValid(Player player) {
            return !backpack.isEmpty() && player.getItemInHand(hand) == backpack;
        }
    }
}
