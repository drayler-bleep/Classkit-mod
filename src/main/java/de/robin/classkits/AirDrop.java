package de.robin.classkits;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Air Drops: alle 120 Minuten faellt eine Kiste mit einem zufaelligen Item vom Himmel. */
public final class AirDrop {
    private AirDrop() {
    }

    /** 120 Minuten in Ticks. */
    private static final int INTERVAL_TICKS = 120 * 60 * 20;
    private static final int RADAR_RANGE = 60;
    private static final int RADAR_GLOW_TICKS = 15 * 20;
    private static final int CHICKEN_TICKS = 60 * 20;
    private static final int FREEZE_BALL_TICKS = 3 * 20;
    private static final Identifier CHICKEN_ID = Identifier.fromNamespaceAndPath(ClassKitsMod.MOD_ID, "chicken_size");

    private static int timer = INTERVAL_TICKS;
    private static int tickCounter = 0;
    private static final Map<UUID, Integer> chicken = new HashMap<>();
    private static final List<Active> active = new ArrayList<>();

    private record Active(BlockPos pos, int[] age) {
    }

    // ------------------------------------------------------------------ Tick

    public static void tick(MinecraftServer server) {
        tickCounter++;
        if (--timer <= 0) {
            timer = INTERVAL_TICKS;
            spawnDrop(server);
        }

        // Huehner-Groesse ablaufen lassen
        for (UUID id : new ArrayList<>(chicken.keySet())) {
            int left = chicken.get(id) - 1;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (left <= 0 || p == null) {
                chicken.remove(id);
                if (p != null) {
                    removeChicken(p);
                }
            } else {
                chicken.put(id, left);
            }
        }

        tickFalling(server);

        // Zertifikate zeigen das Bild der eigenen Waffe
        if (tickCounter % 20 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                updateCertificates(p);
            }
        }

        // Lichtsaeule ueber noch ungeoeffneten Drops
        if (tickCounter % 20 == 0) {
            ServerLevel level = server.overworld();
            for (Active a : new ArrayList<>(active)) {
                a.age()[0]++;
                BlockEntity be = level.getBlockEntity(a.pos());
                boolean gone = !(be instanceof Container c) || c.isEmpty() || a.age()[0] > 3600;
                if (gone) {
                    active.remove(a);
                    continue;
                }
                for (int k = 1; k <= 12; k += 3) {
                    level.sendParticles(ParticleTypes.END_ROD, a.pos().getX() + 0.5, a.pos().getY() + k,
                            a.pos().getZ() + 0.5, 2, 0.1, 0.3, 0.1, 0.0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Drop

    /** Ein Air Drop, der gerade am Fallschirm herunterkommt. */
    private static final class Falling {
        final int id;
        final int x;
        final int z;
        double y;
        final double landY;

        Falling(int id, int x, int z, double y, double landY) {
            this.id = id;
            this.x = x;
            this.z = z;
            this.y = y;
            this.landY = landY;
        }
    }

    private static final List<Falling> falling = new ArrayList<>();
    private static int nextId = 1;
    /** Hoehe, aus der der Fallschirm startet, und Hoehe der Kiste ueber ihrem Boden (Modell mal 3). */
    private static final int START_HEIGHT = 100;
    private static final double CRATE_OFFSET = 1.5;

    private static String tagOf(Falling f) {
        return "ck_drop_" + f.id;
    }

    /** Sucht einen Platz in der Weltgrenze und laesst dort einen Air Drop vom Himmel schweben. */
    public static BlockPos spawnDrop(MinecraftServer server) {
        ServerLevel level = server.overworld();
        RandomSource r = level.getRandom();
        for (int attempt = 0; attempt < 25; attempt++) {
            // innerhalb der Weltgrenze bleiben (mit 10 Bloecken Abstand zum Rand)
            WorldBorder border = level.getWorldBorder();
            int minX = (int) Math.ceil(border.getMinX()) + 10;
            int maxX = (int) Math.floor(border.getMaxX()) - 10;
            int minZ = (int) Math.ceil(border.getMinZ()) + 10;
            int maxZ = (int) Math.floor(border.getMaxZ()) - 10;
            if (maxX <= minX || maxZ <= minZ) {
                return null;
            }
            int x = minX + r.nextInt(maxX - minX + 1);
            int z = minZ + r.nextInt(maxZ - minZ + 1);
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos.below()).getFluidState().isEmpty()) {
                continue; // nicht ins Wasser
            }
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            Falling f = new Falling(nextId++, x, z, y + START_HEIGHT + CRATE_OFFSET, y + CRATE_OFFSET);
            falling.add(f);
            // Chunk geladen halten, bis der Drop gelandet ist
            Cmd.run(server, "forceload add " + x + " " + z);
            Cmd.run(server, "execute in minecraft:overworld run summon item_display " + (x + 0.5) + " " + f.y + " "
                    + (z + 0.5) + " {item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":"
                    + "\"classkits:air_drop\"}},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                    + "translation:[0f,0f,0f],scale:[3f,3f,3f]},teleport_duration:10,view_range:4f,"
                    + "Tags:[\"classkits_airdrop\",\"" + tagOf(f) + "\"]}");
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("An air drop is falling from the sky and lands in 50 seconds near x: " + x
                            + " z: " + z)
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
            return pos;
        }
        return null;
    }

    /** Laesst alle Fallschirme ein Stueck sinken (alle 10 Ticks 1 Block, also 100 Bloecke in 50 Sekunden). */
    private static void tickFalling(MinecraftServer server) {
        if (falling.isEmpty() || tickCounter % 10 != 0) {
            return;
        }
        for (Falling f : new ArrayList<>(falling)) {
            f.y -= 1.0;
            if (f.y <= f.landY) {
                falling.remove(f);
                land(server, f);
            } else {
                Cmd.run(server, "execute in minecraft:overworld as @e[tag=" + tagOf(f)
                        + ",limit=1] run tp @s " + (f.x + 0.5) + " " + f.y + " " + (f.z + 0.5));
                // Rauchspur, damit man den Drop auch von weitem sieht
                Cmd.run(server, "execute in minecraft:overworld run particle minecraft:campfire_cosy_smoke "
                        + (f.x + 0.5) + " " + (f.y + 3.5) + " " + (f.z + 0.5) + " 0.3 0.2 0.3 0.01 3 force");
                Cmd.run(server, "execute in minecraft:overworld run particle minecraft:end_rod "
                        + (f.x + 0.5) + " " + (f.y + 0.5) + " " + (f.z + 0.5) + " 0.4 0.4 0.4 0.01 2 force");
            }
        }
    }

    /** Der Fallschirm ist unten angekommen: Kiste mit Loot setzen und im Chat melden. */
    private static void land(MinecraftServer server, Falling f) {
        ServerLevel level = server.overworld();
        Cmd.run(server, "kill @e[tag=" + tagOf(f) + "]");
        Cmd.run(server, "forceload remove " + f.x + " " + f.z);
        RandomSource r = level.getRandom();
        level.getChunk(f.x >> 4, f.z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, f.x, f.z);
        BlockPos pos = new BlockPos(f.x, y, f.z);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container c) {
            c.setItem(13, new ItemStack(roll(r)));
            be.setChanged();
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setPos(f.x + 0.5, y, f.z + 0.5);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        active.add(new Active(pos, new int[] {0}));
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("An air drop has landed at x: " + f.x + " y: " + y + " z: " + f.z)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
    }

    /** 30% Radar, 25% Coinflip, 20% Huehner-Groesse, 10% Freeze-Ball, 10% Phoenix-Kralle, 5% Zertifikat. */
    private static Item roll(RandomSource r) {
        int n = r.nextInt(100);
        if (n < 30) return AirDropItems.RADAR;
        if (n < 55) return AirDropItems.COINFLIP;
        if (n < 75) return AirDropItems.CHICKEN;
        if (n < 85) return AirDropItems.FREEZE;
        if (n < 95) return AirDropItems.PHOENIX;
        return AirDropItems.CERTIFICATE;
    }

    // ------------------------------------------------------------------ Items

    /** Gibt true zurueck, wenn das Item verbraucht werden soll. */
    public static boolean use(DropItem.Type type, ServerPlayer p, ItemStack stack) {
        MinecraftServer server = ((ServerLevel) p.level()).getServer();
        switch (type) {
            case RADAR: {
                int n = 0;
                for (ServerPlayer o : server.getPlayerList().getPlayers()) {
                    if (o != p && o.level() == p.level() && o.distanceTo(p) <= RADAR_RANGE) {
                        o.addEffect(new MobEffectInstance(MobEffects.GLOWING, RADAR_GLOW_TICKS, 0, false, false, false));
                        n++;
                    }
                }
                Cmd.at(server, p.getUUID(), "playsound classkits:radar_scan player @a ~ ~ ~ 1.5 1");
                Cmd.actionbar(server, p.getUUID(), "Radar: " + n + " Spieler in " + RADAR_RANGE + " Blöcken", "green");
                return true;
            }
            case COINFLIP: {
                Cmd.at(server, p.getUUID(), "playsound classkits:coin_flip player @a ~ ~ ~ 1.2 1");
                if (p.getRandom().nextBoolean()) {
                    p.setHealth(p.getMaxHealth());
                    p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30 * 20, 0));
                    Cmd.title(server, p.getUUID(), "Kopf!", "green");
                    Cmd.at(server, p.getUUID(), "playsound minecraft:entity.player.levelup player @a ~ ~ ~ 1 1.2",
                            "particle minecraft:heart ~ ~1.5 ~ 0.4 0.3 0.4 0 10");
                } else {
                    p.setHealth(1.0F);
                    Cmd.title(server, p.getUUID(), "Zahl!", "red");
                    Cmd.at(server, p.getUUID(), "playsound minecraft:entity.wither.hurt player @a ~ ~ ~ 0.6 1.5");
                }
                return true;
            }
            case CHICKEN: {
                AttributeInstance inst = p.getAttribute(Attributes.SCALE);
                if (inst == null) return false;
                inst.removeModifier(CHICKEN_ID);
                inst.addTransientModifier(new AttributeModifier(CHICKEN_ID, -0.6,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                chicken.put(p.getUUID(), CHICKEN_TICKS);
                Cmd.at(server, p.getUUID(), "playsound classkits:chicken_size player @a ~ ~ ~ 1.2 1",
                        "particle minecraft:poof ~ ~0.5 ~ 0.3 0.3 0.3 0.05 15");
                Cmd.actionbar(server, p.getUUID(), "Du bist jetzt so klein wie ein Huhn!", "yellow");
                return true;
            }
            case FREEZE: {
                FreezeBall ball = new FreezeBall(p.level(), p, stack.copyWithCount(1));
                ball.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0F, 1.5F, 1.0F);
                p.level().addFreshEntity(ball);
                Cmd.at(server, p.getUUID(), "playsound minecraft:entity.snowball.throw player @a ~ ~ ~ 0.8 0.8");
                return true;
            }
            case CERTIFICATE: {
                KitStore.Entry e = ClassKitsMod.store().get(p.getUUID());
                if (e == null) {
                    p.sendSystemMessage(Component.literal("Wähle zuerst eine Klasse mit /kit.").withStyle(ChatFormatting.RED));
                    return false;
                }
                if (e.level() >= KitStore.MAX_LEVEL) {
                    p.sendSystemMessage(Component.literal("Deine Waffe hat schon Level 5.").withStyle(ChatFormatting.RED));
                    return false;
                }
                if (ClassKitsMod.store().addKill(p.getUUID())) {
                    Cmd.at(server, p.getUUID(), "playsound classkits:level_certificate player @a ~ ~ ~ 1.2 1");
                    ClassKitsMod.onLevelUp(p, ClassKitsMod.store().get(p.getUUID()));
                }
                return true;
            }
            default:
                return false;
        }
    }

    /** Setzt bei Level-Up-Zertifikaten im Inventar das Bild passend zur Klasse des Spielers. */
    private static void updateCertificates(ServerPlayer p) {
        KitStore.Entry e = ClassKitsMod.store().get(p.getUUID());
        if (e == null) {
            return;
        }
        Identifier model = Identifier.fromNamespaceAndPath(ClassKitsMod.MOD_ID,
                "level_certificate_" + e.kit().name().toLowerCase(java.util.Locale.ROOT));
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(AirDropItems.CERTIFICATE) && !model.equals(s.get(DataComponents.ITEM_MODEL))) {
                s.set(DataComponents.ITEM_MODEL, model);
            }
        }
    }

    private static void removeChicken(ServerPlayer p) {
        AttributeInstance inst = p.getAttribute(Attributes.SCALE);
        if (inst != null) {
            inst.removeModifier(CHICKEN_ID);
        }
    }

    public static void onDeath(UUID id) {
        chicken.remove(id);
    }

    /** Phoenix-Kralle: wirkt wie ein Totem. Gibt true zurueck, wenn der Tod verhindert wurde. */
    public static boolean tryPhoenix(ServerPlayer p, DamageSource source) {
        if (source.is(DamageTypes.GENERIC_KILL)) {
            return false; // /kill und Combat-Log-Tod lassen sich nicht abwenden
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(AirDropItems.PHOENIX)) {
                s.shrink(1);
                p.setHealth(10.0F);
                p.removeAllEffects();
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 15 * 20, 1));
                p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 15 * 20, 0));
                p.level().broadcastEntityEvent(p, (byte) 35);
                MinecraftServer server = ((ServerLevel) p.level()).getServer();
                Cmd.title(server, p.getUUID(), "Phönix-Kralle!", "gold");
                Cmd.at(server, p.getUUID(), "playsound classkits:phoenix_claw player @a ~ ~ ~ 1.5 1");
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Freeze-Ball

    /** Ein Schneeball, der getroffene Spieler 3 Sekunden einfriert. */
    public static final class FreezeBall extends Snowball {
        public FreezeBall(Level level, LivingEntity owner, ItemStack stack) {
            super(level, owner, stack);
        }

        @Override
        protected void onHitEntity(EntityHitResult result) {
            Entity hit = result.getEntity();
            if (hit instanceof ServerPlayer target && getOwner() instanceof ServerPlayer owner
                    && target != owner && !TeamManager.sameTeam(owner, target)) {
                CombatAbilities.freeze(target, FREEZE_BALL_TICKS, owner);
                Cmd.at(target.level().getServer(), target.getUUID(),
                        "playsound classkits:freeze_ball player @a ~ ~ ~ 1.5 1",
                        "particle minecraft:snowflake ~ ~1 ~ 0.4 0.6 0.4 0.05 30");
            }
            super.onHitEntity(result);
        }
    }

    // ------------------------------------------------------------------ Befehl

    /** Entfernt alle noch ungeoeffneten Air-Drop-Kisten. Gibt die Anzahl zurueck. */
    public static int deleteDrops(MinecraftServer server) {
        ServerLevel level = server.overworld();
        int n = 0;
        for (Active a : new ArrayList<>(active)) {
            BlockEntity be = level.getBlockEntity(a.pos());
            if (be instanceof Container c) {
                c.clearContent();
            }
            if (level.getBlockState(a.pos()).is(Blocks.CHEST)) {
                level.removeBlock(a.pos(), false);
                n++;
            }
        }
        active.clear();
        for (Falling f : new ArrayList<>(falling)) {
            Cmd.run(server, "kill @e[tag=" + tagOf(f) + "]");
            Cmd.run(server, "forceload remove " + f.x + " " + f.z);
            n++;
        }
        falling.clear();
        return n;
    }

    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("airdrop")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawn").executes(ctx -> {
                    BlockPos where = spawnDrop(ctx.getSource().getServer());
                    if (where == null) {
                        ctx.getSource().sendFailure(Component.literal("Kein passender Platz gefunden, versuch es nochmal."));
                        return 0;
                    }
                    return 1;
                }))
                .then(Commands.literal("delete").executes(ctx -> {
                    int n = deleteDrops(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal(n + " Air Drop(s) entfernt."), true);
                    return n;
                })));
    }
}
