package dev.appliedapotheosis.simulator;

import dev.appliedapotheosis.AppliedApotheosis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerLevel;

/** Portable form of a gateway's live summoned reward, so the exact entity can enter item storage. */
public final class CapturedRewardItem extends Item {
    public CapturedRewardItem() { super(new Properties().stacksTo(16)); }
    private static void stripIdentity(CompoundTag tag) {
        tag.remove("UUID"); tag.remove("Pos"); tag.remove("Motion");
        for (var child : tag.getList("Passengers",10)) stripIdentity((CompoundTag)child);
    }
    public static ItemStack capture(Entity entity) {
        var tag = new CompoundTag();
        if (!entity.save(tag)) throw new IllegalArgumentException("Reward entity cannot be saved");
        stripIdentity(tag);
        var result = new ItemStack(AppliedApotheosis.CAPTURED_REWARD.get());
        result.set(DataComponents.ENTITY_DATA, CustomData.of(tag));
        result.set(DataComponents.CUSTOM_NAME, Component.literal("Captured ").append(entity.getName()));
        return result;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        var stack = context.getItemInHand(); var data = stack.get(DataComponents.ENTITY_DATA);
        if (data == null) return InteractionResult.FAIL;
        var tag = data.copyTag(); stripIdentity(tag);
        var entity = EntityType.loadEntityRecursive(tag, server, e -> e);
        if (entity == null) return InteractionResult.FAIL;
        var pos = context.getClickedPos().relative(context.getClickedFace());
        entity.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        if (!server.noCollision(entity)) return InteractionResult.FAIL;
        if (!server.tryAddFreshEntityWithPassengers(entity)) return InteractionResult.FAIL;
        stack.shrink(1); return InteractionResult.CONSUME;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Gateway summon reward; use on a block to release."));
    }
}
