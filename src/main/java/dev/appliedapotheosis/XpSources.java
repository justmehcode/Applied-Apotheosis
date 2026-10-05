package dev.appliedapotheosis;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import dev.appliedapotheosis.simulator.SimulatorConfig;

/** Additional fluids are consumed in whole-point batches; refunds preserve odd raw remainders. */
public final class XpSources {
    public static boolean validRate(String value) {
        try {
            String[] parts=value.split("=",-1);
            return parts.length==2 && ResourceLocation.tryParse(parts[0])!=null
                    && Integer.parseInt(parts[1])>0 && Integer.parseInt(parts[1])<=1000000;
        } catch (NumberFormatException e) { return false; }
    }
    public static long transfer(MEStorage storage, IActionSource source, long limit, Actionable mode,
            java.util.function.Consumer<ItemStack> refund) {
        if (storage==null || limit<=0) return 0;
        long total=0;
        var seen=new java.util.HashSet<ResourceLocation>();
        seen.add(ControllerBlockEntity.XP_FLUID);
        for(String entry:SimulatorConfig.XP_FLUIDS.get()) {
            String[] parts=entry.split("="); var id=ResourceLocation.parse(parts[0]);
            if(!seen.add(id) || !BuiltInRegistries.FLUID.containsKey(id)) continue;
            long rate=Long.parseLong(parts[1]);
            var key=AEFluidKey.of(BuiltInRegistries.FLUID.get(id));
            long points=Math.min((limit-total)/20,Long.MAX_VALUE/rate);
            long available=storage.extract(key,points*rate,Actionable.SIMULATE,source)/rate;
            long raw=mode==Actionable.SIMULATE ? available*rate : storage.extract(key,available*rate,mode,source);
            total+=(raw/rate)*20;
            long remainder=raw%rate;
            if(mode==Actionable.MODULATE && remainder>0) {
                long returned=storage.insert(key,remainder,Actionable.MODULATE,source);
                if(returned<remainder) refund.accept(XpReceipt.createFluid(id,remainder-returned));
            }
            if(limit-total<20) break;
        }
        return total;
    }
}
