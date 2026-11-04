package de.robin.classkits;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Das Schild: Rechtsklick (gedrueckt halten) blockt wie ein normales Schild.
 * Sneak + Rechtsklick loest die Block-Faehigkeit aus.
 */
public final class BlockKitItem extends Item {
    public BlockKitItem(Item.Properties properties) {
        super(properties);
    }

    /** Vanilla-Schild-Verhalten (gleiche Werte wie das normale Schild, nur ohne Haltbarkeit). */
    public static BlocksAttacks shieldComponent() {
        return new BlocksAttacks(0.25F, 1.0F,
                List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
                new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
                Optional.of(DamageTypeTags.BYPASSES_SHIELD),
                Optional.of(SoundEvents.SHIELD_BLOCK),
                Optional.of(SoundEvents.SHIELD_BREAK));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            // normales Blocken
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            int left = CombatAbilities.blockLeft(player.getUUID());
            if (left > 0) {
                sp.sendSystemMessage(Component.literal("Block-Fähigkeit noch " + ((left + 19) / 20) + " s im Cooldown."));
                return InteractionResult.FAIL;
            }
            CombatAbilities.markBlockCooldown(player.getUUID());
            CombatAbilities.startGuard(sp);
        }
        return InteractionResult.SUCCESS;
    }
}
