package de.robin.classkits;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Faehigkeiten von Block (Schild) und Dolch sowie die Schadens-/Kampf-Regeln. */
public final class CombatAbilities {
    // --- Werte (hier kann man alles schnell anpassen) ---
    public static final int BLOCK_COOLDOWN_TICKS = 600;      // 30 s
    public static final int DASH_COOLDOWN_TICKS = 400;       // 20 s
    public static final double DASH_DISTANCE = 8.0;          // Bloecke
    public static final float BLOCK_HEAL = 4.0F;             // ab Level 3 (2 Herzen)
    public static final int RIPOSTE_TICKS = 100;             // 5 s Zeit fuer den Punch
    public static final int FREEZE_TICKS = 40;               // 2 s
    public static final int POISON_TICKS = 60;               // 3 s
    private static final float DAGGER_DAMAGE = 5.0F;
    private static final float DAGGER_DAMAGE_LVL5 = 7.0F;

    private static final Map<UUID, Integer> guarding = new HashMap<>();
    private static final Map<UUID, Integer> riposteReady = new HashMap<>();
    private static final Map<UUID, Integer> stunned = new HashMap<>();
    private static final Map<UUID, Long> repairCd = new HashMap<>();
    private static final Map<UUID, Long> boltCd = new HashMap<>();
    /** Hammer Level 4: Opfer -> Angreifer, der innerhalb von 3 s 15% Durchbruch-Schaden verursacht. */
    private static final Map<UUID, UUID> markAttacker = new HashMap<>();
    private static final Map<UUID, Integer> markTime = new HashMap<>();
    private static final Map<UUID, Double> airFall = new HashMap<>();
    /** Dolch Level 4: vom Dash brennende Opfer -> Angreifer (fuer den Level-5-Durchbruch). */
    private static final Map<UUID, UUID> burnAttacker = new HashMap<>();
    private static final Map<UUID, Integer> burnTime = new HashMap<>();
    /** Lila Partikel-Aura nach einem Level-Up (Restdauer in Ticks). */
    private static final Map<UUID, Integer> aura = new HashMap<>();
    private static long tickCounter = 0;
    private static boolean inPierce = false;

    public static final int REPAIR_COOLDOWN_TICKS = 600;     // 30 s
    public static final int BOLT_COOLDOWN_TICKS = 240;       // 12 s
    public static final int REPAIR_AMOUNT = 100;
    private static final String REPAIRED_KEY = "classkits_repaired";

    private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath("classkits", "dagger_speed");
    private static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath("classkits", "dagger_damage");
    private static final Identifier HAMMER_DAMAGE_ID = Identifier.fromNamespaceAndPath("classkits", "hammer_damage");

    private CombatAbilities() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(CombatAbilities::allowDamage);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !(entity instanceof LivingEntity target)) {
                return InteractionResult.PASS;
            }
            if (sp.getItemInHand(hand).is(ClassKitsMod.BLOCK) && riposteReady.containsKey(sp.getUUID())) {
                riposteReady.remove(sp.getUUID());
                punch(sp, target);
                return InteractionResult.FAIL;
            }
            // Hammer Level 3: Sneak + Linksklick = Blitz
            if (sp.getItemInHand(hand).is(ClassKitsMod.HAMMER) && sp.isShiftKeyDown()
                    && levelFor(sp, Kit.HAMMER) >= 3) {
                if (target instanceof ServerPlayer tp && TeamManager.sameTeam(sp, tp)) {
                    return InteractionResult.PASS;
                }
                if (!hammerLightning(sp, target)) {
                    return InteractionResult.PASS; // Cooldown: normaler Schlag
                }
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        // Hammer Level 4: 15% des Schadens gehen durch die Ruestung (3 s nach dem Blitz-Stun)
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (blocked || damageTaken <= 0 || !(entity instanceof ServerPlayer victim)) return;
            if (!(source.getEntity() instanceof ServerPlayer attacker) || !source.is(DamageTypes.PLAYER_ATTACK)) return;
            if (attacker == victim || !attacker.getMainHandItem().is(ClassKitsMod.HAMMER)
                    || levelFor(attacker, Kit.HAMMER) < 4) return;
            if (!attacker.getUUID().equals(markAttacker.get(victim.getUUID()))
                    || markTime.getOrDefault(victim.getUUID(), 0) <= 0) return;
            ServerLevel sl = (ServerLevel) victim.level();
            victim.invulnerableTime = 0;
            victim.hurtServer(sl, sl.damageSources().indirectMagic(attacker, attacker), baseDamage * 0.15F);
        });
        ServerTickEvents.END_SERVER_TICK.register(CombatAbilities::tick);
    }

    // ------------------------------------------------------------------
    // Hilfsfunktionen
    // ------------------------------------------------------------------

    private static MinecraftServer serverOf(ServerPlayer p) {
        return ((ServerLevel) p.level()).getServer();
    }

    /** Level des Spielers fuer die angegebene Klasse (1, falls er eine andere Klasse hat). */
    public static int levelFor(ServerPlayer p, Kit kit) {
        KitStore.Entry e = ClassKitsMod.store().get(p.getUUID());
        return e != null && e.kit() == kit ? e.level() : 1;
    }

    private static boolean isStunned(Entity e) {
        return stunned.getOrDefault(e.getUUID(), 0) > 0;
    }

    private static boolean isBurningFrom(ServerPlayer attacker, Entity victim) {
        return attacker.getUUID().equals(burnAttacker.get(victim.getUUID()))
                && burnTime.getOrDefault(victim.getUUID(), 0) > 0;
    }

    private static float daggerDamage(ServerPlayer p) {
        return levelFor(p, Kit.DAGGER) >= 5 ? DAGGER_DAMAGE_LVL5 : DAGGER_DAMAGE;
    }

    // ------------------------------------------------------------------
    // Block
    // ------------------------------------------------------------------

    public static int guardTicks(int level) {
        if (level >= 3) return 80;
        if (level == 2) return 60;
        return 40;
    }

    public static void startGuard(ServerPlayer p) {
        int level = levelFor(p, Kit.BLOCK);
        int ticks = guardTicks(level);
        guarding.put(p.getUUID(), ticks);
        p.sendSystemMessage(Component.literal("Block aktiv (" + (ticks / 20) + " Sekunden)."));
        MinecraftServer server = serverOf(p);
        Cmd.at(server, p.getUUID(),
                "playsound minecraft:item.armor.equip_iron player @a ~ ~ ~ 1.0 0.8",
                "playsound minecraft:block.beacon.activate player @a ~ ~ ~ 0.5 1.7",
                "particle minecraft:enchanted_hit ~ ~1 ~ 0.5 0.7 0.5 0.15 30");
        if (level >= 3) {
            p.heal(BLOCK_HEAL);
            Cmd.at(server, p.getUUID(), "particle minecraft:heart ~ ~1.5 ~ 0.4 0.3 0.4 0 6");
        }
    }

    private static void onBlockedHit(ServerPlayer victim, DamageSource source) {
        UUID id = victim.getUUID();
        guarding.remove(id);
        riposteReady.put(id, RIPOSTE_TICKS);
        victim.sendSystemMessage(Component.literal("Angriff geblockt! Linksklick fuer den Punch."));
        MinecraftServer server = serverOf(victim);
        Cmd.at(server, id,
                "playsound minecraft:item.shield.block player @a ~ ~ ~ 1.2 1.0",
                "playsound minecraft:block.anvil.land player @a ~ ~ ~ 0.4 1.9",
                "playsound minecraft:block.amethyst_block.chime player @a ~ ~ ~ 1.0 1.5",
                "particle minecraft:crit ~ ~1 ~ 0.6 0.6 0.6 0.4 40",
                "particle minecraft:electric_spark ~ ~1 ~ 0.5 0.5 0.5 0.3 25");

        Entity attacker = source.getEntity();
        if (attacker == null || source.getDirectEntity() != attacker) return; // nur Nahkampf
        int level = levelFor(victim, Kit.BLOCK);
        if (level >= 5 && attacker instanceof LivingEntity target) {
            ServerLevel sl = (ServerLevel) victim.level();
            float thorns = 1.0F + victim.getRandom().nextInt(4); // wie Dornen III: 1-4 Schaden
            target.hurtServer(sl, sl.damageSources().thorns(victim), thorns);
        }
    }

    private static double punchKnockback(int level) {
        if (level >= 4) return 1.4;
        if (level >= 2) return 1.0;
        return 0.66;
    }

    private static void punch(ServerPlayer p, LivingEntity target) {
        if (target instanceof ServerPlayer tp && TeamManager.sameTeam(p, tp)) return;
        MinecraftServer server = serverOf(p);
        ServerLevel sl = (ServerLevel) p.level();
        double dx = target.getX() - p.getX();
        double dz = target.getZ() - p.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            dx = 0;
            dz = 1;
            len = 1;
        }
        dx /= len;
        dz /= len;
        int level = levelFor(p, Kit.BLOCK);
        double kb = punchKnockback(level);

        target.hurtServer(sl, sl.damageSources().playerAttack(p), 8.0F);
        target.setDeltaMovement(dx * kb, level >= 4 ? 0.45 : 0.35, dz * kb);
        target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 0), p);
        // Block Level 4: der Gegenschlag friert Spieler 2 Sekunden ein
        if (level >= 4 && target instanceof ServerPlayer tp) {
            tp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FREEZE_TICKS, 255, false, true, true), p);
            stunned.put(tp.getUUID(), FREEZE_TICKS);
            Cmd.at(server, tp.getUUID(), "particle minecraft:snowflake ~ ~1 ~ 0.4 0.6 0.4 0.02 25");
        }

        p.setDeltaMovement(dx * 0.6, 0.1, dz * 0.6);
        p.hurtMarked = true;

        Cmd.at(server, target.getUUID(),
                "playsound minecraft:entity.generic.explode player @a ~ ~ ~ 0.7 1.5",
                "playsound minecraft:item.mace.smash_ground player @a ~ ~ ~ 1.0 1.0",
                "playsound minecraft:entity.player.attack.knockback player @a ~ ~ ~ 1.0 0.9",
                "particle minecraft:explosion ~ ~1 ~ 0 0 0 0 1",
                "particle minecraft:cloud ~ ~0.5 ~ 0.4 0.3 0.4 0.15 25",
                "particle minecraft:crit ~ ~1 ~ 0.5 0.5 0.5 0.6 40");
        Cmd.at(server, p.getUUID(),
                "playsound minecraft:entity.player.attack.sweep player @a ~ ~ ~ 1.0 0.8",
                "particle minecraft:sweep_attack ~ ~1 ~ 0.3 0.2 0.3 0 3");
    }

    // ------------------------------------------------------------------
    // Dolch: Dash
    // ------------------------------------------------------------------

    private static boolean validDashTarget(ServerPlayer self, LivingEntity e) {
        if (e == self || !e.isAlive() || e instanceof ArmorStand) return false;
        if (e instanceof ServerPlayer other && TeamManager.sameTeam(self, other)) return false;
        return true;
    }

    /** Dash in Blickrichtung (horizontal, auch im Lauf oder Sprung), maximal 8 Bloecke. Gibt true zurueck, wenn der Dash stattfand. */
    public static boolean dash(ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        MinecraftServer server = level.getServer();

        Vec3 look = p.getLookAngle();
        double hx = look.x;
        double hz = look.z;
        double len = Math.sqrt(hx * hx + hz * hz);
        if (len < 1.0E-4) {
            double yaw = Math.toRadians(p.getYRot());
            hx = -Math.sin(yaw);
            hz = Math.cos(yaw);
        } else {
            hx /= len;
            hz /= len;
        }

        AABB base = p.getBoundingBox();
        double step = 0.25;
        double travelled = 0.0;
        double yOffset = 0.0;
        LivingEntity hit = null;

        while (travelled + step <= DASH_DISTANCE + 1.0E-9) {
            double next = travelled + step;
            AABB box = base.move(hx * next, yOffset, hz * next);
            if (!level.noCollision(p, box)) {
                // eine Stufe (1 Block) hochsteigen, falls moeglich
                AABB up = base.move(hx * next, yOffset + 1.0, hz * next);
                if (yOffset == 0.0 && level.noCollision(p, up)) {
                    yOffset = 1.0;
                    box = up;
                } else {
                    break;
                }
            }
            List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box.inflate(0.2),
                    e -> validDashTarget(p, e));
            if (!found.isEmpty()) {
                hit = found.get(0);
                break;
            }
            travelled = next;
        }

        if (travelled < 0.5 && hit == null) {
            p.sendSystemMessage(Component.literal("Kein Platz fuer den Dash."));
            return false;
        }

        Cmd.at(server, p.getUUID(),
                "playsound minecraft:item.trident.riptide_1 player @a ~ ~ ~ 1.0 1.3",
                "particle minecraft:cloud ~ ~0.2 ~ 0.3 0.1 0.3 0.05 15",
                "particle minecraft:sweep_attack ~ ~1 ~ 0.3 0.2 0.3 0 3");

        p.teleportTo(p.getX() + hx * travelled, p.getY() + yOffset, p.getZ() + hz * travelled);
        p.fallDistance = 0;

        Cmd.at(server, p.getUUID(),
                "playsound minecraft:entity.phantom.flap player @a ~ ~ ~ 0.8 1.6",
                "particle minecraft:dust{color:[0.5,0.1,0.8],scale:1.0} ~ ~1 ~ 0.25 0.4 0.25 0 12",
                "particle minecraft:large_smoke ~ ~0.9 ~ 0.2 0.3 0.2 0.01 3");

        if (hit != null) {
            dashHit(server, p, hit);
        }
        return true;
    }

    private static void dashHit(MinecraftServer server, ServerPlayer p, LivingEntity target) {
        int level = levelFor(p, Kit.DAGGER);
        ServerLevel sl = (ServerLevel) p.level();

        target.hurtServer(sl, sl.damageSources().playerAttack(p), daggerDamage(p));

        if (level >= 3) {
            // Gift I fuer 3 Sekunden
            target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0), p);
        }
        // Level 4: der Dash-Treffer setzt den Gegner in Brand (4 s)
        if (level >= 4) {
            target.igniteForSeconds(4.0F);
            burnAttacker.put(target.getUUID(), p.getUUID());
            burnTime.put(target.getUUID(), 80);
            Cmd.at(server, target.getUUID(), "particle minecraft:flame ~ ~1 ~ 0.3 0.5 0.3 0.03 25");
        }

        Cmd.at(server, target.getUUID(),
                "playsound minecraft:entity.player.attack.crit player @a ~ ~ ~ 1.2 0.9",
                "playsound minecraft:item.trident.hit player @a ~ ~ ~ 1.0 1.2",
                "particle minecraft:damage_indicator ~ ~1 ~ 0.3 0.4 0.3 0.1 12",
                "particle minecraft:crit ~ ~1 ~ 0.5 0.6 0.5 0.5 40");
    }

    // ------------------------------------------------------------------
    // Hammer
    // ------------------------------------------------------------------

    private static boolean onCooldown(Map<UUID, Long> map, ServerPlayer p, String what) {
        Long until = map.get(p.getUUID());
        if (until != null && until > tickCounter) {
            long seconds = (until - tickCounter + 19) / 20;
            Cmd.actionbar(serverOf(p), p.getUUID(), what + " Cooldown: " + seconds + " s", "gray");
            return true;
        }
        return false;
    }

    /** Level 2: Werkzeug/Ruestung am Boden anschauen, Sneak + Rechtsklick = +100 Haltbarkeit (1x pro Item). */
    public static void hammerRepair(ServerPlayer p) {
        if (onCooldown(repairCd, p, "Reparatur")) return;
        ServerLevel level = (ServerLevel) p.level();
        MinecraftServer server = level.getServer();

        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(5.0));
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(eye, end).inflate(1.0))) {
            Optional<Vec3> hit = ie.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < bestDist) {
                    bestDist = d;
                    best = ie;
                }
            }
        }
        if (best == null) {
            p.sendSystemMessage(Component.literal("Schau auf ein Werkzeug oder Rüstungsteil am Boden."));
            return;
        }
        ItemStack stack = best.getItem().copy();
        if (!stack.isDamageableItem()) {
            p.sendSystemMessage(Component.literal("Nur Werkzeuge und Rüstung können repariert werden."));
            return;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(REPAIRED_KEY)) {
            p.sendSystemMessage(Component.literal("Dieses Item wurde schon einmal repariert."));
            return;
        }
        if (stack.getDamageValue() <= 0) {
            p.sendSystemMessage(Component.literal("Dieses Item ist nicht beschädigt."));
            return;
        }
        stack.setDamageValue(Math.max(0, stack.getDamageValue() - REPAIR_AMOUNT));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(REPAIRED_KEY, true));
        best.setItem(stack);
        repairCd.put(p.getUUID(), tickCounter + REPAIR_COOLDOWN_TICKS);

        String pos = String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", best.getX(), best.getY() + 0.3, best.getZ());
        Cmd.run(server, "playsound minecraft:block.anvil.use player @a " + pos + " 0.8 1.4");
        Cmd.run(server, "particle minecraft:happy_villager " + pos + " 0.3 0.3 0.3 0 15");
        p.sendSystemMessage(Component.literal("Item repariert (+" + REPAIR_AMOUNT + " Haltbarkeit)."));
    }

    /** Level 3: Sneak + Linksklick = Blitz von oben (Stun, halbes Herz). false = Cooldown aktiv. */
    private static boolean hammerLightning(ServerPlayer p, LivingEntity target) {
        if (onCooldown(boltCd, p, "Blitz")) return false;
        ServerLevel sl = (ServerLevel) p.level();
        int level = levelFor(p, Kit.HAMMER);

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setPos(target.getX(), target.getY(), target.getZ());
            bolt.setVisualOnly(true); // kein eigener Schaden, kein Feuer
            sl.addFreshEntity(bolt);
        }
        boltCd.put(p.getUUID(), tickCounter + BOLT_COOLDOWN_TICKS);

        target.hurtServer(sl, sl.damageSources().indirectMagic(p, p), 1.0F); // halbes Herz

        // Stun (Slowness 255) nur gegen Spieler: 1 s, ab Level 4 2,5 s
        if (target instanceof ServerPlayer tp) {
            int ticks = level >= 4 ? 50 : 20;
            tp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 255, false, true, true), p);
            stunned.put(tp.getUUID(), ticks);
            if (level >= 4) {
                markAttacker.put(tp.getUUID(), p.getUUID());
                markTime.put(tp.getUUID(), 60); // 3 s Zeit fuer den Durchbruch
            }
        }
        return true;
    }

    /** Level 5: Landung nach >= 2 Bloecken Fall stoesst alles in 3 Bloecken weg. */
    private static void slam(ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        MinecraftServer server = level.getServer();
        AABB box = p.getBoundingBox().inflate(3.0);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> validDashTarget(p, e) && e.distanceTo(p) <= 3.0F)) {
            double dx = t.getX() - p.getX();
            double dz = t.getZ() - p.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.001) {
                dx = 0;
                dz = 1;
                len = 1;
            }
            t.setDeltaMovement(dx / len * 1.3, 0.6, dz / len * 1.3);
            t.hurtMarked = true;
        }
        Cmd.at(server, p.getUUID(),
                "playsound minecraft:item.mace.smash_ground player @a ~ ~ ~ 1.2 0.8",
                "playsound minecraft:entity.generic.explode player @a ~ ~ ~ 0.8 1.2",
                "particle minecraft:explosion ~ ~0.2 ~ 1.2 0.1 1.2 0 6",
                "particle minecraft:cloud ~ ~0.2 ~ 1.5 0.1 1.5 0.1 40");
    }

    // ------------------------------------------------------------------
    // Schaden
    // ------------------------------------------------------------------

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        Entity attackerEntity = source.getEntity();
        ServerPlayer attacker = attackerEntity instanceof ServerPlayer a ? a : null;

        if (entity instanceof ServerPlayer victim) {
            // Hammer Level 5: kein Fallschaden mit dem Hammer in der Hand
            if (source.is(DamageTypes.FALL) && victim.getMainHandItem().is(ClassKitsMod.HAMMER)
                    && levelFor(victim, Kit.HAMMER) >= 5) {
                return false;
            }
            if (attacker != null && attacker != victim) {
                // Teammates koennen sich nicht schlagen und loesen keinen Combat-Timer aus
                if (TeamManager.sameTeam(attacker, victim)) {
                    return false;
                }
                CombatTimer.tag(attacker, victim);
            }

            // Block faengt Treffer von Entities ab
            if (attackerEntity != null && !source.is(DamageTypes.THORNS)
                    && guarding.getOrDefault(victim.getUUID(), 0) > 0) {
                onBlockedHit(victim, source);
                return false;
            }

            // Dolch Level 5: Treffer auf eingefrorene Spieler gehen durch die Ruestung
            if (attacker != null && attacker != victim && !inPierce
                    && source.is(DamageTypes.PLAYER_ATTACK)
                    && attacker.getMainHandItem().is(ClassKitsMod.DAGGER)
                    && levelFor(attacker, Kit.DAGGER) >= 5
                    && (isStunned(victim) || isBurningFrom(attacker, victim))) {
                ServerLevel sl = (ServerLevel) victim.level();
                inPierce = true;
                try {
                    victim.hurtServer(sl, sl.damageSources().indirectMagic(attacker, attacker), amount);
                } finally {
                    inPierce = false;
                }
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    private static void setModifier(ServerPlayer p, Holder<Attribute> attribute, Identifier id, double amount,
                                    AttributeModifier.Operation op, boolean enabled) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        boolean has = inst.hasModifier(id);
        AttributeModifier current = inst.getModifier(id);
        if (has && current != null && current.amount() != amount) {
            inst.removeModifier(id);
            has = false;
        }
        if (enabled && !has) {
            inst.addTransientModifier(new AttributeModifier(id, amount, op));
        } else if (!enabled && has) {
            inst.removeModifier(id);
        }
    }

    private static void tick(MinecraftServer server) {
        tickCounter++;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();

            // Dolch: +10% Lauftempo ab Level 2, +2 Schaden ab Level 5 (nur in der Hand)
            KitStore.Entry e = ClassKitsMod.store().get(id);
            ItemStack main = p.getMainHandItem();
            boolean dagger = e != null && e.kit() == Kit.DAGGER && main.is(ClassKitsMod.DAGGER);
            setModifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.10,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, dagger && e.level() >= 2);
            setModifier(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID, 2.0,
                    AttributeModifier.Operation.ADD_VALUE, dagger && e.level() >= 5);

            // Hammer: Schaden 4 -> 5 -> 6 -> 6 -> 9 (Bonus auf die Basis von 4)
            boolean hammer = e != null && e.kit() == Kit.HAMMER && main.is(ClassKitsMod.HAMMER);
            double hammerBonus = 0.0;
            if (hammer) {
                int lvl = e.level();
                hammerBonus = lvl >= 5 ? 5.0 : lvl >= 3 ? 2.0 : lvl == 2 ? 1.0 : 0.0;
            }
            setModifier(p, Attributes.ATTACK_DAMAGE, HAMMER_DAMAGE_ID, hammerBonus,
                    AttributeModifier.Operation.ADD_VALUE, hammerBonus > 0.0);

            // Hammer Level 5: Landung nach mindestens 2 Bloecken Fall = Schockwelle
            double fall = p.fallDistance;
            if (!p.onGround()) {
                airFall.merge(id, fall, Math::max);
            } else {
                Double f = airFall.remove(id);
                if (f != null && f >= 2.0 && hammer && e.level() >= 5) {
                    slam(p);
                }
            }
        }

        if (tickCounter % 5 == 0) {
            for (UUID id : new ArrayList<>(guarding.keySet())) {
                Cmd.at(server, id, "particle minecraft:electric_spark ~ ~1 ~ 0.45 0.75 0.45 0.02 2");
            }
            for (UUID id : new ArrayList<>(riposteReady.keySet())) {
                Cmd.at(server, id, "particle minecraft:end_rod ~ ~1.1 ~ 0.3 0.5 0.3 0.02 2");
            }
            for (UUID id : new ArrayList<>(stunned.keySet())) {
                Cmd.at(server, id, "particle minecraft:snowflake ~ ~2.0 ~ 0.3 0.05 0.3 0.02 4");
            }
        }

        tickDown(guarding);
        tickDown(riposteReady);
        tickDown(stunned);
        tickDown(markTime);
        tickDown(burnTime);
        burnAttacker.keySet().removeIf(k -> !burnTime.containsKey(k));
        markAttacker.keySet().removeIf(k -> !markTime.containsKey(k));

        tickAura(server);
        CombatTimer.tick(server);
        MaxedEffect.tick(server);
        MaxedEffect.tick(server);
        ClassKitsMod.tickRestore(server);
        ClassKitsMod.tickItems(server);
    }

    private static void tickDown(Map<UUID, Integer> map) {
        map.replaceAll((k, v) -> v - 1);
        map.entrySet().removeIf(en -> en.getValue() <= 0);
    }

    /** Startet die lila Partikel um den Spieler (ca. 3 Sekunden). */
    public static void startAura(ServerPlayer p) {
        aura.put(p.getUUID(), 60);
        // Blitz nur als Optik: kein Schaden, kein Feuer
        ServerLevel sl = (ServerLevel) p.level();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setPos(p.getX(), p.getY(), p.getZ());
            bolt.setVisualOnly(true);
            sl.addFreshEntity(bolt);
        }
        Cmd.at(serverOf(p), p.getUUID(),
                "particle minecraft:electric_spark ~ ~1 ~ 0.6 0.9 0.6 0.3 40");
        Cmd.at(serverOf(p), p.getUUID(),
                "particle minecraft:dust{color:[0.6,0.2,0.95],scale:1.5} ~ ~1 ~ 0.7 0.9 0.7 0 80",
                "particle minecraft:witch ~ ~1 ~ 0.6 0.9 0.6 0.1 40");
    }

    private static void tickAura(MinecraftServer server) {
        if (aura.isEmpty()) return;
        if (tickCounter % 2 == 0) {
            for (UUID id : new ArrayList<>(aura.keySet())) {
                Cmd.at(server, id,
                        "particle minecraft:dust{color:[0.6,0.2,0.95],scale:1.2} ~ ~1 ~ 0.6 0.9 0.6 0 10",
                        "particle minecraft:witch ~ ~1 ~ 0.5 0.8 0.5 0.05 4");
            }
        }
        tickDown(aura);
    }

    /** Raeumt Zustaende auf, wenn ein Spieler stirbt. */
    public static void onDeath(UUID id) {
        guarding.remove(id);
        riposteReady.remove(id);
        stunned.remove(id);
        aura.remove(id);
    }
}
