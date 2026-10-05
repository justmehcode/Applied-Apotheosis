package dev.appliedapotheosis.simulator;

import java.util.List;
import java.util.UUID;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import dev.shadowsoffire.apotheosis.compat.gateways.*;
import dev.shadowsoffire.apotheosis.compat.gateways.tiered_gate.TieredGateway;
import dev.shadowsoffire.gateways.gate.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Loaded only when Apotheosis is present. Native factories retain all affix/gem components. */
public final class ApotheosisSupport {
    public static boolean tiered(Gateway gate) { return gate instanceof TieredGateway; }
    public static List<Wave> waves(Gateway gate) { return ((TieredGateway)gate).waves(); }
    public static List<Reward> rewards(Gateway gate) { return ((TieredGateway)gate).rewards(); }
    public static boolean supports(Reward reward) {
        return reward instanceof AffixItemReward || reward instanceof GemReward || reward instanceof TrueRandomGemReward;
    }
    public static void capture(Player owner, CompoundTag context) {
        context.putInt("tier", WorldTier.getTier(owner).ordinal());
        context.putDouble("luck", owner.getLuck());
    }
    public static boolean refresh(ServerLevel level, UUID owner, CompoundTag context, Player simulator) {
        var real = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (real != null) capture(real, context);
        simulator.setData(Apoth.Attachments.WORLD_TIER, WorldTier.BY_ID.apply(context.getInt("tier")));
        simulator.getAttribute(Attributes.LUCK).setBaseValue(context.getDouble("luck"));
        return context.contains("tier");
    }
    public static void prepareElite(net.minecraft.world.entity.LivingEntity entity, Player player) {
        if (!(entity instanceof net.minecraft.world.entity.Mob mob)) return;
        var data = mob.getPersistentData();
        String key = dev.shadowsoffire.apotheosis.mobs.types.Elite.MINIBOSS_KEY;
        if (!data.contains(key, net.minecraft.nbt.Tag.TAG_STRING)) return;
        var elite = dev.shadowsoffire.apotheosis.mobs.registries.EliteRegistry.INSTANCE.getValue(
                net.minecraft.resources.ResourceLocation.parse(data.getString(key)));
        if (elite == null) throw new IllegalArgumentException("Unknown Apotheosis elite");
        elite.transformMiniboss((ServerLevel) mob.level(), mob,
                dev.shadowsoffire.apotheosis.tiers.GenContext.forPlayerAtPos(mob.getRandom(), player, mob.blockPosition()));
    }
}
