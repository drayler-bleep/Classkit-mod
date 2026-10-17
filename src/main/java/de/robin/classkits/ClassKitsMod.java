package de.robin.classkits;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ClassKitsMod implements ModInitializer {
    public static final String MOD_ID = "classkits";

    public static final Item BLOCK = register("block", new BlockKitItem(itemProperties("block", "Block")));
    public static final Item DAGGER = register("dagger",
            new DaggerKitItem(itemProperties("dagger", "Dolch").attributes(DaggerKitItem.baseAttributes())));
    public static final Item HAMMER = register("hammer",
            new HammerKitItem(itemProperties("hammer", "Hammer").attributes(HammerKitItem.baseAttributes())));

    private static final KitStore STORE = new KitStore();
    /** Spieler, deren Waffen beim Tod entfernt wurden und wieder zurueckkommen muessen. */
    private static final Set<UUID> pendingRestore = new HashSet<>();
    /** Frisch gespawnte Kit-Waffen als Item-Entity (z.B. durch Q-Taste); werden zurueckgegeben. */
    private static final java.util.List<ItemEntity> droppedItems = new ArrayList<>();

    @Override
    public void onInitialize() {
        STORE.load(FabricLoader.getInstance().getConfigDir().resolve("classkits-players.txt"));
        TeamManager.load(FabricLoader.getInstance().getConfigDir().resolve("classkits-teams.txt"));

        CombatAbilities.register();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayer p = handler.player;
            TeamManager.updateName(p);
            if (STORE.get(p.getUUID()) == null) {
                p.sendSystemMessage(Component.literal("Wähle deine Klasse mit /kit").withStyle(ChatFormatting.YELLOW));
            }
        }));

        // Ausloggen im Kampf = Tod
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer p = handler.player;
            if (server.isRunning() && CombatTimer.inCombat(p.getUUID())) {
                CombatTimer.punishLogout(server, p);
            }
        });

        // Kit-Waffen sollen beim Tod nicht gedroppt werden (sonst gaebe es sie nur einmal)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer p && STORE.get(p.getUUID()) != null) {
                Inventory inv = p.getInventory();
                boolean removed = false;
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (KitItems.isKitItem(inv.getItem(i))) {
                        inv.setItem(i, ItemStack.EMPTY);
                        removed = true;
                    }
                }
                if (removed) pendingRestore.add(p.getUUID());
            }
            return true;
        });

        // Kills zaehlen: nur Spieler toeten bringt Level
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer victim) {
                CombatTimer.clear(victim.getUUID());
                CombatAbilities.onDeath(victim.getUUID());
                Entity killer = source.getEntity();
                if (killer instanceof ServerPlayer attacker && attacker != victim) {
                    awardKill(attacker);
                }
            }
        });

        // Waffen koennen nicht weggeworfen werden
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity ie && KitItems.isKitItem(ie.getItem())) {
                droppedItems.add(ie);
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) restoreWeapon(newPlayer);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerKitCommand(dispatcher);
            TeamCommand.register(dispatcher);
        });
    }

    // ------------------------------------------------------------------

    public static KitStore store() {
        return STORE;
    }

    /** Gibt die Waffe der gewaehlten Klasse zurueck ins Inventar, falls sie fehlt. */
    private static void restoreWeapon(ServerPlayer p) {
        pendingRestore.remove(p.getUUID());
        KitStore.Entry e = STORE.get(p.getUUID());
        if (e == null) return;
        if (!KitItems.hasKitItem(p.getInventory(), e.kit())) {
            p.getInventory().add(KitItems.create(e.kit(), e.level()));
        }
    }

    /** Fuer Faelle, in denen der Spieler den Tod "ueberlebt" (z.B. Totem). */
    public static void tickRestore(MinecraftServer server) {
        if (pendingRestore.isEmpty()) return;
        for (UUID id : new java.util.ArrayList<>(pendingRestore)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.isAlive()) restoreWeapon(p);
        }
    }

    /**
     * Waffen-Schutz: Weggeworfene Waffen wandern zurueck ins Inventar, und Waffen,
     * die in Kisten & Co. gelegt werden, springen wieder ins Spieler-Inventar.
     */
    public static void tickItems(MinecraftServer server) {
        if (!droppedItems.isEmpty()) {
            for (ItemEntity ie : new ArrayList<>(droppedItems)) {
                droppedItems.remove(ie);
                if (ie.isRemoved()) continue;
                if (ie.level().getNearestPlayer(ie, 6.0) instanceof ServerPlayer sp) {
                    ItemStack back = ie.getItem().copy();
                    if (sp.getInventory().add(back)) {
                        ie.discard();
                    }
                }
            }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            AbstractContainerMenu m = p.containerMenu;
            if (m instanceof KitMenu) continue;
            for (Slot slot : m.slots) {
                if (slot.container instanceof Inventory || !slot.hasItem()) continue;
                ItemStack st = slot.getItem();
                if (!KitItems.isKitItem(st)) continue;
                ItemStack copy = st.copy();
                slot.set(ItemStack.EMPTY);
                if (!p.getInventory().add(copy)) {
                    slot.set(copy); // Inventar voll: bleibt liegen
                }
            }
        }
    }

    /** Ein Spieler-Kill: Level steigt, ab Dolch-Level 4 volle Heilung. */
    public static void awardKill(ServerPlayer attacker) {
        boolean levelUp = STORE.addKill(attacker.getUUID());
        KitStore.Entry e = STORE.get(attacker.getUUID());
        if (e == null) return;
        MinecraftServer server = attacker.level().getServer();
        if (levelUp) {
            KitItems.refresh(attacker, e);
            CombatAbilities.startAura(attacker);
            attacker.sendSystemMessage(Component.literal("Level-Up! " + e.kit().displayName() + " ist jetzt Level "
                    + e.level() + ": " + e.kit().unlock(e.level())).withStyle(ChatFormatting.GREEN));
            Cmd.at(server, attacker.getUUID(),
                    "playsound minecraft:entity.player.levelup player @a ~ ~ ~ 0.8 1.2",
                    "particle minecraft:totem_of_undying ~ ~1 ~ 0.4 0.6 0.4 0.3 30");
            if (e.level() >= KitStore.MAX_LEVEL) {
                MaxedEffect.play(attacker, e.kit());
            }
        }
        if (levelUp && e.level() >= KitStore.MAX_LEVEL) {
            MaxedEffect.start(attacker, e.kit());
        }
        if (e.kit() == Kit.DAGGER && e.level() >= 4) {
            attacker.setHealth(attacker.getMaxHealth());
            Cmd.at(server, attacker.getUUID(), "particle minecraft:heart ~ ~1.5 ~ 0.4 0.3 0.4 0 8");
        }
    }

    /** Waehlt die Klasse, gibt die Waffe und zeigt den Namen gross auf dem Bildschirm. */
    public static boolean select(ServerPlayer p, Kit kit) {
        if (!STORE.choose(p.getUUID(), kit)) {
            p.sendSystemMessage(Component.literal("Du hast bereits eine Klasse gewählt.").withStyle(ChatFormatting.RED));
            return false;
        }
        p.getInventory().add(KitItems.create(kit, 1));
        MinecraftServer server = p.level().getServer();
        Cmd.title(server, p.getUUID(), kit.shortName(), kit.color());
        Cmd.at(server, p.getUUID(), "playsound minecraft:ui.toast.challenge_complete player @s ~ ~ ~ 0.8 1.2");
        p.sendSystemMessage(Component.literal("Du hast die " + kit.displayName() + " gewählt!").withStyle(ChatFormatting.GOLD));
        return true;
    }

    // ------------------------------------------------------------------

    private static void registerKitCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kit")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    KitStore.Entry e = STORE.get(p.getUUID());
                    if (e != null) {
                        p.sendSystemMessage(Component.literal("Du hast schon die " + e.kit().displayName()
                                + " (Level " + e.level() + ").").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    KitMenu.open(p);
                    return 1;
                })
                .then(Commands.literal("choose")
                        .then(Commands.argument("klasse", StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Kit kit = Kit.parse(StringArgumentType.getString(ctx, "klasse"));
                            if (kit == null) {
                                ctx.getSource().sendFailure(Component.literal("Nutze: /kit choose block, dolch oder hammer"));
                                return 0;
                            }
                            return select(p, kit) ? 1 : 0;
                        })))
                .then(Commands.literal("info").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    KitStore.Entry e = STORE.get(p.getUUID());
                    ctx.getSource().sendSuccess(() -> Component.literal(e == null
                            ? "Noch keine Klasse gewählt."
                            : e.kit().displayName() + " | Level " + e.level() + " | Kills: " + e.kills()), false);
                    return 1;
                }))
                // nur von der Server-Konsole (oder Command-Bloecken)
                .then(Commands.literal("reset").requires(src -> src.getEntity() == null)
                        .then(Commands.argument("spieler-uuid", StringArgumentType.word()).executes(ctx -> {
                            UUID id = UUID.fromString(StringArgumentType.getString(ctx, "spieler-uuid"));
                            STORE.reset(id);
                            MaxedEffect.revoke(ctx.getSource().getServer(), id);
                            for (Kit k : Kit.values()) {
                                Cmd.run(ctx.getSource().getServer(), "advancement revoke " + id + " only " + MaxedEffect.advancementId(k));
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Klasse zurückgesetzt: " + id), true);
                            return 1;
                        }))));
    }

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, name), item);
    }

    private static Item.Properties itemProperties(String name, String displayName) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, name);
        return new Item.Properties().stacksTo(1)
                .component(DataComponents.ITEM_NAME, Component.literal(displayName))
                .setId(ResourceKey.create(Registries.ITEM, id));
    }

    public static Item itemOf(Kit kit) {
        switch (kit) {
            case BLOCK: return BLOCK;
            case DAGGER: return DAGGER;
            default: return HAMMER;
        }
    }
}
