package de.robin.classkits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Ein Air-Drop-Item; Rechtsklick loest die Faehigkeit aus (Phoenix-Kralle wirkt automatisch beim Tod). */
public final class DropItem extends Item {
    public enum Type { RADAR, COINFLIP, CHICKEN, FREEZE, PHOENIX, CERTIFICATE }

    private final Type type;

    public DropItem(Item.Properties properties, Type type) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (type == Type.PHOENIX) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (AirDrop.use(type, sp, stack)) {
            stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }
}
