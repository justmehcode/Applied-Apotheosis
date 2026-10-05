package dev.appliedapotheosis.simulator;

import java.util.*;
import java.lang.reflect.Method;
import dev.appliedapotheosis.AppliedApotheosis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/** Captures the real death-loot hooks without adding mobs, items or XP to the world. */
@EventBusSubscriber(modid = AppliedApotheosis.ID)
public final class SimulationLoot implements AutoCloseable {
    private static final ThreadLocal<SimulationLoot> ACTIVE = new ThreadLocal<>();
    private static final Method DROP;
    static {
        try { DROP = LivingEntity.class.getDeclaredMethod("dropAllDeathLoot", ServerLevel.class, DamageSource.class); DROP.setAccessible(true); }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    public final List<ItemStack> items = new ArrayList<>();
    public long xp;
    private final Set<LivingEntity> killed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<LivingEntity> spawned = new ArrayList<>();
    private int spawnedCursor;
    private boolean splittingSpawner;
    private final Set<LivingEntity> splitEligible = Collections.newSetFromMap(new IdentityHashMap<>());
    int entityRolls() { return killed.size(); }
    public void killSpawner(LivingEntity entity, Player player) { splitEligible.add(entity); kill(entity, player); }
    public void drainSpawned(Player player) {
        while (spawnedCursor < spawned.size()) {
            if (spawnedCursor > 2048) throw new IllegalStateException("Simulation support-entity limit exceeded");
            var entity = spawned.get(spawnedCursor++);
            for (var member : entity.getPassengersAndSelf().toList()) if (member instanceof LivingEntity living) kill(living, player);
        }
    }
    private final ServerLevel level;
    private final SimulationLoot previous;
    public SimulationLoot(ServerLevel level) { this.level = level; previous = ACTIVE.get(); ACTIVE.set(this); }
    public void add(ItemStack stack) { if (!stack.isEmpty()) items.add(stack.copy()); }
    public void kill(LivingEntity entity, Player player) {
        if (!killed.add(entity)) return;
        try {
            if (net.neoforged.fml.ModList.get().isLoaded("apotheosis")) ApotheosisSupport.prepareElite(entity, player);
            entity.tickCount = 100;
            entity.setLastHurtByPlayer(player);
            var damage = level.damageSources().playerAttack(player);
            // Native loot and LivingDrops handlers see the complete held sword. Apply native ignition
            // conditions too; unrelated combat/world effects are not part of a loot simulation.
            net.minecraft.world.item.enchantment.EnchantmentHelper.runIterationOnItem(player.getWeaponItem(), (ench, rank) -> {
                for (var effect : ench.value().getEffects(net.minecraft.world.item.enchantment.EnchantmentEffectComponents.POST_ATTACK))
                    if (effect.enchanted() == net.minecraft.world.item.enchantment.EnchantmentTarget.ATTACKER
                            && effect.affected() == net.minecraft.world.item.enchantment.EnchantmentTarget.VICTIM
                            && effect.effect() instanceof net.minecraft.world.item.enchantment.effects.Ignite)
                        net.minecraft.world.item.enchantment.Enchantment.doPostAttack(effect, level, rank,
                                new net.minecraft.world.item.enchantment.EnchantedItemInUse(player.getWeaponItem(), net.minecraft.world.entity.EquipmentSlot.MAINHAND, player), entity, damage);
            });
            DROP.invoke(entity, level, damage);
            if (splitEligible.contains(entity) && entity instanceof net.minecraft.world.entity.monster.Slime slime && slime.getSize() > 1) {
                // Invoke vanilla/NeoForge splitting, including MobSplitEvent; capture children before
                // they enter the world and roll each child with the same sword through drainSpawned.
                boolean previousSplitting = splittingSpawner;
                splittingSpawner = true;
                try { entity.setHealth(0); entity.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED); }
                finally { splittingSpawner = previousSplitting; }
            }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot simulate entity loot", e); }
        finally { if (!entity.isRemoved()) entity.discard(); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void captureExperience(LivingExperienceDropEvent event) {
        var capture = ACTIVE.get();
        if (capture != null && event.getEntity().level() == capture.level) {
            capture.xp += Math.max(0, event.getDroppedExperience()); event.setDroppedExperience(0);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void captureLiving(EntityJoinLevelEvent event) {
        var capture = ACTIVE.get();
        if (capture != null && event.getLevel() == capture.level && event.getEntity() instanceof LivingEntity entity && !(entity instanceof Player)) {
            capture.spawned.add(entity);
            if (capture.splittingSpawner) capture.splitEligible.add(entity);
            event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void captureEntity(EntityJoinLevelEvent event) {
        var capture = ACTIVE.get();
        if (capture == null || event.getLevel() != capture.level || event.isCanceled()) return;
        if (event.getEntity() instanceof ItemEntity item) { capture.add(item.getItem()); event.setCanceled(true); }
        else if (event.getEntity() instanceof ExperienceOrb orb) { capture.xp += orb.getValue(); event.setCanceled(true); }
    }
    @Override public void close() { spawned.forEach(entity -> { if (!entity.isRemoved()) entity.discard(); }); if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
}
