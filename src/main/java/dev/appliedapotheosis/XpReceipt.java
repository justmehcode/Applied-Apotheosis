package dev.appliedapotheosis;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import java.util.List;

/** Exact refund for the exceptional case where a third-party ME storage extracts less than simulated. */
public final class XpReceipt extends Item {
    public XpReceipt() { super(new Properties().stacksTo(1)); }
    public static ItemStack create(long amount) {
        ItemStack stack = new ItemStack(AppliedApotheosis.XP_RECEIPT.get());
        CompoundTag tag = new CompoundTag(); tag.putLong("xp_mb", amount);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); return stack;
    }
    private static long amount(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong("xp_mb");
    }
    public static ItemStack createFluid(net.minecraft.resources.ResourceLocation fluid, long amount) {
        var stack=create(amount);
        var tag=stack.get(DataComponents.CUSTOM_DATA).copyTag(); tag.putString("fluid",fluid.toString());
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag)); return stack;
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // Redeem before the controller's normal right-click opens its menu.
        return useOn(context);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var target = context.getLevel().getBlockEntity(context.getClickedPos());
        appeng.api.networking.IManagedGridNode node;
        appeng.api.networking.security.IActionHost host;
        appeng.api.stacks.AEFluidKey key;
        if (target instanceof ControllerBlockEntity be) { node = be.node(); host = be; key = be.xpKey(); }
        else if (target instanceof dev.appliedapotheosis.simulator.SimulatorBlockEntity be) {
            node = be.node(); host = be;
            key = appeng.api.stacks.AEFluidKey.of(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(ControllerBlockEntity.XP_FLUID));
        } else return InteractionResult.PASS;
        var receiptData=context.getItemInHand().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(receiptData.contains("fluid")) {
            var id=net.minecraft.resources.ResourceLocation.tryParse(receiptData.getString("fluid"));
            if(id==null || !net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(id)) return InteractionResult.FAIL;
            key=appeng.api.stacks.AEFluidKey.of(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id));
        }
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        long amount = amount(context.getItemInHand());
        if (amount <= 0 || !node.isActive() || key == null || context.getPlayer() == null) return InteractionResult.FAIL;
        long inserted = node.getGrid().getStorageService().getInventory().insert(key, amount,
                Actionable.MODULATE, IActionSource.ofPlayer(context.getPlayer(), host));
        if (inserted == amount) context.getItemInHand().shrink(1);
        else if (inserted > 0) { receiptData.putLong("xp_mb",amount-inserted); context.getItemInHand().set(DataComponents.CUSTOM_DATA,CustomData.of(receiptData)); }
        return inserted > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        tooltip.add(Component.literal(amount(stack) + " mB of " + (tag.contains("fluid") ? tag.getString("fluid") : "Just Dire Things XP")));
        tooltip.add(Component.literal("Use on a powered Infusion Controller or ME Simulator to return it to ME storage."));
    }
}
