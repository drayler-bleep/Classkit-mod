package de.robin.classkits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class BlockKitItem extends Item {
    public BlockKitItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(stack)) {
                return InteractionResult.FAIL;
            }
            player.getCooldowns().addCooldown(stack, CombatAbilities.BLOCK_COOLDOWN_TICKS);
            CombatAbilities.startGuard(sp);
        }
        return InteractionResult.SUCCESS;
    }
}
