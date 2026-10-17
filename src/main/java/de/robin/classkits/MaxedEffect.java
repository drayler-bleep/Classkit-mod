package de.robin.classkits;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Effekt, wenn eine Waffe Level 5 erreicht:
 * lila Challenge-Toast + Sound + Chat, Bildschirm wackelt, danach Waffe gross im Bild (Totem-Animation,
 * nur Partikel, keine Effekte) und lila Titel "Maxed Weapon".
 */
public final class MaxedEffect {
    private static final int SHAKE_END = 12;
    private static final int SHOW_AT = 14;

    private static final Map<UUID, Integer> time = new HashMap<>();
    private static final Map<UUID, Kit> kits = new HashMap<>();

    private MaxedEffect() {
    }

    private static String name(Kit kit) {
        switch (kit) {
            case BLOCK: return "block";
            case DAGGER: return "dagger";
            default: return "hammer";
        }
    }

    public static void start(ServerPlayer p, Kit kit) {
        MinecraftServer server = ((ServerLevel) p.level()).getServer();
        String adv = "classkits:maxed_" + name(kit);
        // erst zuruecknehmen, damit der lila Toast jedes Mal neu erscheint (inkl. Challenge-Sound)
        Cmd.run(server, "advancement revoke " + p.getUUID() + " only " + adv);
        Cmd.run(server, "advancement grant " + p.getUUID() + " only " + adv);

        server.getPlayerList().broadcastSystemMessage(
                Component.literal(p.getName().getString() + " has achieved ")
                        .append(Component.literal("[Maxed Weapon]").withStyle(ChatFormatting.LIGHT_PURPLE)),
                false);

        time.put(p.getUUID(), 0);
        kits.put(p.getUUID(), kit);
    }

    /** Entfernt den Erfolg wieder (z.B. nach /kit reset), damit er spaeter neu vergeben werden kann. */
    public static void revoke(MinecraftServer server, UUID id) {
        Cmd.run(server, "advancement revoke " + id + " only classkits:maxed_block");
        Cmd.run(server, "advancement revoke " + id + " only classkits:maxed_dagger");
        Cmd.run(server, "advancement revoke " + id + " only classkits:maxed_hammer");
    }

    public static void tick(MinecraftServer server) {
        if (time.isEmpty()) return;
        for (UUID id : new ArrayList<>(time.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                time.remove(id);
                kits.remove(id);
                continue;
            }
            int t = time.get(id);
            if (t <= SHAKE_END && t % 2 == 0) {
                // abwechselnd links/rechts -> der Bildschirm wackelt (kein Schaden)
                float yaw = p.getYRot() + (t % 4 == 0 ? 90.0F : -90.0F);
                p.connection.send(new ClientboundHurtAnimationPacket(p.getId(), yaw));
            }
            if (t == SHOW_AT) {
                show(server, p, kits.get(id));
            }
            if (t >= SHOW_AT) {
                time.remove(id);
                kits.remove(id);
            } else {
                time.put(id, t + 1);
            }
        }
    }

    /**
     * Totem-Animation mit dem Modell der Waffe: Der Client zeigt beim Totem-Event den Totem aus der Hand.
     * Wir schicken kurz einen Totem mit Waffen-Modell in die Offhand (nur beim Client), loesen das Event aus
     * und stellen die echte Offhand sofort wieder her. Es gibt nur Partikel, keine Effekte.
     */
    private static void show(MinecraftServer server, ServerPlayer p, Kit kit) {
        ItemStack fake = new ItemStack(Items.TOTEM_OF_UNDYING);
        fake.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("classkits", kit.modelId(5)));

        int stateId = p.inventoryMenu.getStateId();
        p.connection.send(new ClientboundContainerSetSlotPacket(0, stateId, 45, fake));
        p.connection.send(new ClientboundEntityEventPacket(p, (byte) 35));
        p.connection.send(new ClientboundContainerSetSlotPacket(0, stateId, 45, p.getOffhandItem()));

        // Totem-Sound unterdruecken, der Challenge-Sound vom Toast soll der einzige sein
        Cmd.run(server, "stopsound " + p.getUUID() + " player minecraft:item.totem.use");
        Cmd.title(server, p.getUUID(), "Maxed Weapon", "light_purple");
    }
}
