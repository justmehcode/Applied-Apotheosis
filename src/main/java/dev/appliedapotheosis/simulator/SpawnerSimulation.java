package dev.appliedapotheosis.simulator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.SpawnData;
import net.neoforged.neoforge.event.EventHooks;

public final class SpawnerSimulation {
    public static boolean accepts(ItemStack stack) { return stack.is(Items.SPAWNER); }
    public static CompoundTag data(ItemStack stack) { return stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag(); }
    public static int count(ItemStack stack) { var nbt = data(stack); return nbt.contains("SpawnCount") ? Math.max(0, nbt.getShort("SpawnCount")) : 4; }
    public static int delay(ItemStack stack, ServerLevel level) {
        var nbt = data(stack); int min = nbt.contains("MinSpawnDelay") ? Math.max(1, nbt.getShort("MinSpawnDelay")) : 200;
        int max = nbt.contains("MaxSpawnDelay") ? Math.max(min, nbt.getShort("MaxSpawnDelay")) : 800;
        return min + (max > min ? level.random.nextInt(max-min) : 0);
    }
    public static void run(ItemStack stack, ServerLevel level, BlockPos pos, Player player, SimulationLoot loot) {
        var nbt = data(stack); CompoundTag spawn = nbt.getCompound("SpawnData");
        if (nbt.contains("SpawnPotentials")) {
            var choices = SpawnData.LIST_CODEC.parse(NbtOps.INSTANCE, nbt.get("SpawnPotentials")).getOrThrow();
            var chosen = choices.getRandomValue(level.random);
            if (chosen.isPresent()) spawn = SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, chosen.get()).getOrThrow() instanceof CompoundTag c ? c : spawn;
        }
        var spawnDefinition = SpawnData.CODEC.parse(NbtOps.INSTANCE, spawn).getOrThrow();
        var entityData = spawnDefinition.getEntityToSpawn();
        if (entityData.isEmpty()) throw new IllegalArgumentException("Spawner has no entity data");
        final int spawnCount = count(stack);
        if (spawnCount > 256) throw new IllegalArgumentException("Spawner batch exceeds 256 entities");
        var stats = nbt.getCompound("stats");
        // Each spawn gets a fresh entity and its own native loot/XP hooks. Never reuse a
        // rolled result or multiply a single table roll by the spawner's batch size.
        for (int i=0; i<spawnCount; i++) {
            var entity = EntityType.loadEntityRecursive(entityData.copy(), level, e -> e);
            if (!(entity instanceof LivingEntity living)) throw new IllegalArgumentException("Spawner entity is not a living mob");
            living.setPos(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5);
            for (var member : living.getPassengersAndSelf().toList()) {
                if (!(member instanceof LivingEntity target)) continue;
                target.setPos(pos.getCenter());
                if (stats.contains("apothic_spawners:initial_health")) target.setHealth(target.getHealth()*stats.getFloat("apothic_spawners:initial_health"));
                if (target instanceof Mob mob && stats.getBoolean("apothic_spawners:no_ai")) mob.setNoAi(true);
                if (stats.getBoolean("apothic_spawners:silent")) target.setSilent(true);
                if (stats.getBoolean("apothic_spawners:youthful") && target instanceof Mob mob) mob.setBaby(true);
                if (stats.getBoolean("apothic_spawners:burning") && !target.fireImmune()) target.setRemainingFireTicks(Integer.MAX_VALUE);
                target.getPersistentData().putInt("apothic_spawners:echoing", stats.getInt("apothic_spawners:echoing"));
            }
            if (living instanceof Mob mob && entityData.size() == 1)
                EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null);
            if (living instanceof Mob mob) spawnDefinition.getEquipment().ifPresent(mob::equip);
            for (var member : living.getPassengersAndSelf().toList())
                if (member instanceof LivingEntity target) loot.killSpawner(target, player);
            loot.drainSpawned(player);
        }
    }
}
