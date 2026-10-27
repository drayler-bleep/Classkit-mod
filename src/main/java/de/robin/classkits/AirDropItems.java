package de.robin.classkits;

import net.minecraft.world.item.Item;

/** Die sechs Air-Drop-Items. */
public final class AirDropItems {
    private AirDropItems() {
    }

    public static final Item RADAR = ClassKitsMod.register("radar_scanner",
            new DropItem(ClassKitsMod.itemProperties("radar_scanner", "Radar-Scanner"), DropItem.Type.RADAR));
    public static final Item COINFLIP = ClassKitsMod.register("coinflip",
            new DropItem(ClassKitsMod.itemProperties("coinflip", "Coinflip"), DropItem.Type.COINFLIP));
    public static final Item CHICKEN = ClassKitsMod.register("chicken_size",
            new DropItem(ClassKitsMod.itemProperties("chicken_size", "Hühner-Größe"), DropItem.Type.CHICKEN));
    public static final Item FREEZE = ClassKitsMod.register("freeze_ball",
            new DropItem(ClassKitsMod.itemProperties("freeze_ball", "Freeze-Ball"), DropItem.Type.FREEZE));
    public static final Item PHOENIX = ClassKitsMod.register("phoenix_claw",
            new DropItem(ClassKitsMod.itemProperties("phoenix_claw", "Phönix-Kralle"), DropItem.Type.PHOENIX));
    public static final Item CERTIFICATE = ClassKitsMod.register("level_certificate",
            new DropItem(ClassKitsMod.itemProperties("level_certificate", "Level-Up-Zertifikat"), DropItem.Type.CERTIFICATE));

    /** Erzwingt das Laden der Klasse (und damit die Registrierung). */
    public static void init() {
    }
}
