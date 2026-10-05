package dev.appliedapotheosis.simulator;

import java.util.*;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import dev.shadowsoffire.gateways.gate.*;
import dev.shadowsoffire.gateways.gate.normal.NormalGateway;
import dev.shadowsoffire.gateways.gate.endless.EndlessGateway;
import dev.shadowsoffire.gateways.item.GatePearlItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.EventHooks;

/** Optional Gateways implementation. Resolve through SimulatorIntegration only when Gateways is loaded. */
public final class GatewaySimulation {
    private static boolean apotheosis() { return net.neoforged.fml.ModList.get().isLoaded("apotheosis"); }
    private static List<Wave> finiteWaves(Gateway gate) {
        if (gate instanceof NormalGateway normal) return normal.waves();
        if (apotheosis() && ApotheosisSupport.tiered(gate)) return ApotheosisSupport.waves(gate);
        return null;
    }
    private static List<Reward> finalRewards(Gateway gate) {
        if (gate instanceof NormalGateway normal) return normal.rewards();
        return ApotheosisSupport.rewards(gate);
    }
    public static String waitingReason(ItemStack pearl, Player player, boolean knownTier) {
        if (apotheosis() && GatePearlItem.getGate(pearl).getId().getNamespace().equals("apotheosis") && !knownTier)
            return "Owner must use this simulator once to establish world tier";
        var reason = definition(pearl).canOpen(player);
        return reason == null ? "" : reason.getString();
    }
    private static void bindSummoner(GatewayEntity gate, Player player) {
        try {
            var field = GatewayEntity.class.getDeclaredField("knownPlayer"); field.setAccessible(true);
            field.set(gate, new java.lang.ref.WeakReference<>(player));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot bind gateway loot context", e); }
    }
    private static float dropChance(Mob mob, EquipmentSlot slot) {
        try {
            var method = Mob.class.getDeclaredMethod("getEquipmentDropChance", EquipmentSlot.class);
            method.setAccessible(true);
            return (float) method.invoke(mob, slot);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot read equipment drop chance", e); }
    }
    public static boolean accepts(ItemStack stack) { return stack.getItem() instanceof GatePearlItem; }
    public static Gateway definition(ItemStack pearl) {
        var holder = GatePearlItem.getGate(pearl);
        if (!holder.isBound()) throw new IllegalArgumentException("Unknown gateway");
        return holder.get();
    }
    public static void validate(ItemStack pearl) {
        var gate = definition(pearl);
        if (finiteWaves(gate) != null) {
            if (finiteWaves(gate).isEmpty()) throw new IllegalArgumentException("Gateway has no waves");
            finiteWaves(gate).forEach(w -> w.rewards().forEach(GatewaySimulation::checkReward));
            finalRewards(gate).forEach(GatewaySimulation::checkReward);
        } else if (gate instanceof EndlessGateway endless) {
            endless.baseWave().rewards().forEach(GatewaySimulation::checkReward);
            endless.modifiers().forEach(m -> m.rewards().forEach(GatewaySimulation::checkReward));
        } else throw new IllegalArgumentException("Unsupported gateway definition");
    }
    private static void checkReward(Reward reward) {
        if (reward instanceof Reward.ChancedReward r) checkReward(r.reward());
        else if (reward instanceof Reward.CountedReward r) checkReward(r.reward());
        else if (!(apotheosis() && ApotheosisSupport.supports(reward)) && !(reward instanceof Reward.StackReward || reward instanceof Reward.StackListReward
                || reward instanceof Reward.EntityLootReward || reward instanceof Reward.LootTableReward
                || reward instanceof Reward.ExperienceReward || reward instanceof Reward.SummonReward))
            throw new IllegalArgumentException("Gateway has a non-storage reward: " + reward.getClass().getSimpleName());
    }
    private static void reward(Reward reward, ServerLevel level, GatewayEntity gate, Player player, SimulationLoot loot) {
        if (reward instanceof Reward.CountedReward r) {
            for (int i=0; i<r.count(); i++) reward(r.reward(), level, gate, player, loot);
        } else if (reward instanceof Reward.ChancedReward r) {
            if (level.random.nextFloat() < r.chance()) reward(r.reward(), level, gate, player, loot);
        } else if (reward instanceof Reward.SummonReward r) {
            for (int i=0; i<r.entity().getCount(); i++) {
                var entity = r.entity().createEntity(level, gate);
                if (entity == null) throw new IllegalArgumentException("Invalid summoned reward entity");
                try { loot.add(CapturedRewardItem.capture(entity)); } finally { entity.discard(); }
            }
        } else reward.generateLoot(level, gate, player, loot::add);
    }
    public static Wave wave(Gateway gate, int index) {
        return finiteWaves(gate) != null ? finiteWaves(gate).get(index) : ((EndlessGateway)gate).baseWave();
    }
    public static int duration(ItemStack pearl, int index) {
        var gate = definition(pearl); var wave = wave(gate, index); long ticks = (long)wave.maxWaveTime()+wave.setupTime();
        if (gate instanceof EndlessGateway endless) for (var mod : endless.modifiers())
            ticks += (long)mod.appMode().getApplicationCount(index+1)*(mod.waveTime()+mod.setupTime());
        return (int)Math.clamp(ticks, 20, Integer.MAX_VALUE);
    }
    public static boolean finished(ItemStack pearl, int nextWave) {
        var waves = finiteWaves(definition(pearl));
        return waves != null && nextWave >= waves.size();
    }
    public static void runWave(ItemStack pearl, int index, ServerLevel level, BlockPos pos, Player player, SimulationLoot loot) {
        var definition = definition(pearl); var wave = wave(definition, index);
        GatewayEntity gate = definition.createEntity(level, player);
        CompoundTag saved = gate.saveWithoutId(new CompoundTag()); saved.putInt("wave", index); gate.load(saved);
        gate.setPos(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5);
        bindSummoner(gate, player);
        List<WaveEntity> entities = new ArrayList<>(wave.entities());
        List<WaveModifier> extraModifiers = new ArrayList<>(); List<Reward> rewards = new ArrayList<>(wave.rewards());
        if (definition instanceof EndlessGateway endless) for (var mod : endless.modifiers()) {
            int times = mod.appMode().getApplicationCount(index+1);
            if (times > 1024) throw new IllegalArgumentException("Endless wave exceeds simulation budget");
            for (int i=0; i<times; i++) { entities.addAll(mod.entities()); extraModifiers.addAll(mod.modifiers()); rewards.addAll(mod.rewards()); }
        }
        if (entities.stream().mapToLong(WaveEntity::getCount).sum() > 1024)
            throw new IllegalArgumentException("Gateway wave exceeds 1024 entities");
        try {
            for (var entry : entities) for (int i=0; i<entry.getCount(); i++) {
                var living = entry.createEntity(level, gate);
                if (living == null) throw new IllegalArgumentException("Invalid wave entity");
                living.setPos(gate.position());
                living.getPersistentData().putUUID("gateways.owner",gate.getUUID());
                for (var member : living.getPassengersAndSelf().toList()) if (member instanceof LivingEntity target) {
                    target.setPos(gate.position());
                    for (var modifier : wave.modifiers()) modifier.apply(target, gate);
                    target.setHealth(target.getMaxHealth());
                }
                if (living instanceof Mob mob) {
                    if (entry.shouldFinalizeSpawn()) EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null);
                    float chance = definition.rules().defaultDropChance();
                    if (chance >= 0) for (var slot : EquipmentSlot.values())
                        if (slot != EquipmentSlot.BODY && dropChance(mob, slot) == .085F) mob.setDropChance(slot, chance);
                }
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new dev.shadowsoffire.gateways.event.GateEvent.WaveEntitySpawned(gate, living));
                if (apotheosis()) for (var member : living.getPassengersAndSelf().toList())
                    if (member instanceof LivingEntity target) ApotheosisSupport.prepareElite(target, player);
                for (var modifier : extraModifiers) modifier.apply(living, gate);
                living.setHealth(living.getMaxHealth());
                for (var member : living.getPassengersAndSelf().toList()) if (member instanceof LivingEntity target) loot.kill(target, player);
            }
            loot.drainSpawned(player);
            for (var reward : rewards) reward(reward, level, gate, player, loot);
            if (finiteWaves(definition) != null && index+1 == finiteWaves(definition).size())
                for (var reward : finalRewards(definition)) reward(reward, level, gate, player, loot);
        } finally { gate.discard(); }
    }
}
