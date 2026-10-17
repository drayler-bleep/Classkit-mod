package de.robin.classkits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

public final class HammerKitItem extends Item {
    public HammerKitItem(Item.Properties properties) {
        super(properties);
    }

    /** Basiswerte: 4 Angriffsschaden (1 Hand + 3) und 0,9 Angriffstempo (4 - 3,1). */
    public static ItemAttributeModifiers baseAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 3.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, -3.1, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** Sneak + Rechtsklick (ab Level 2): Werkzeug/Ruestung am Boden reparieren. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown() && player instanceof ServerPlayer sp
                && CombatAbilities.levelFor(sp, Kit.HAMMER) >= 2) {
            if (!level.isClientSide()) {
                CombatAbilities.hammerRepair(sp);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
