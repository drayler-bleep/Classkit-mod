package de.robin.classkits;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

public final class DaggerKitItem extends Item {
    public DaggerKitItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Basiswerte des Dolchs: 5 Angriffsschaden (1 Hand + 4) und 1,6 Angriffstempo (4 - 2,4).
     * Die Vanilla-IDs sorgen dafuer, dass der Tooltip "5 Attack Damage / 1.6 Attack Speed" zeigt.
     */
    public static ItemAttributeModifiers baseAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 4.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.4, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(stack)) {
                return InteractionResult.FAIL;
            }
            if (CombatAbilities.levelFor(sp, Kit.DAGGER) < 2) {
                sp.sendSystemMessage(Component.literal("Der Dash wird ab Level 2 freigeschaltet."));
                return InteractionResult.FAIL;
            }
            if (CombatAbilities.dash(sp)) {
                player.getCooldowns().addCooldown(stack, CombatAbilities.DASH_COOLDOWN_TICKS);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
