package dev.appliedapotheosis.compat;

import dev.appliedapotheosis.ControllerBlock;
import dev.appliedapotheosis.ControllerBlockEntity;
import dev.appliedapotheosis.AppliedApotheosis;
import dev.appliedapotheosis.simulator.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;

/** Optional Jade integration, discovered only by Jade. */
@WailaPlugin
public final class InfusionJadePlugin implements IWailaPlugin {
    @Override public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Status.INSTANCE, ControllerBlockEntity.class);
        registration.registerBlockDataProvider(SimulatorStatus.INSTANCE, SimulatorBlockEntity.class);
    }
    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Status.INSTANCE, ControllerBlock.class);
        registration.registerBlockComponent(SimulatorStatus.INSTANCE, SimulatorBlock.class);
    }
    public enum Status implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;
        @Override public ResourceLocation getUid() { return AppliedApotheosis.id("status"); }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof ControllerBlockEntity controller)
                data.put(AppliedApotheosis.ID, controller.inspectionData());
        }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains(AppliedApotheosis.ID)) return;
            var data = accessor.getServerData().getCompound(AppliedApotheosis.ID);
            tooltip.add(Component.translatable("jade.applied_apotheosis.jobs", data.getInt("active"), data.getInt("capacity")));
            tooltip.add(Component.translatable("jade.applied_apotheosis.speed", data.getInt("speed")));
            tooltip.add(Component.translatable("jade.applied_apotheosis.source",
                    Component.translatable(data.getBoolean("personal") ? "jade.applied_apotheosis.personal" : "jade.applied_apotheosis.me")));
            tooltip.add(Component.translatable(data.getBoolean("online") ? "jade.applied_apotheosis.online"
                    : "jade.applied_apotheosis.offline"));
            for (var raw : data.getList("jobs", 10)) {
                var job = (CompoundTag) raw; int remaining = job.getInt("remaining");
                Component progress = remaining < 0 ? Component.translatable("jade.applied_apotheosis.waiting")
                        : remaining == 0 ? Component.translatable("jade.applied_apotheosis.output_full")
                        : Component.literal((100-Math.clamp(remaining, 0, 100))+"%");
                tooltip.add(Component.translatable("jade.applied_apotheosis.job", job.getInt("lane"), job.getString("name"), progress));
            }
        }
    }
    public enum SimulatorStatus implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;
        @Override public ResourceLocation getUid() { return AppliedApotheosis.id("simulator_status"); }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof SimulatorBlockEntity be)) return;
            var status = new CompoundTag(); status.putInt("capacity",be.capacity()); status.putBoolean("online",be.node().isActive());
            status.putBoolean("paused",be.paused); status.putBoolean("blocked",be.blocked());
            status.putBoolean("adjacentOutput",be.adjacentOutput());
            var lanes = new net.minecraft.nbt.ListTag();
            for (int i=0;i<be.capacity();i++) {
                var lane=be.lanes[i]; var stack=lane.pearl.isEmpty()?be.inventory.getStackInSlot(i):lane.pearl;
                if (stack.isEmpty()) continue;
                var tag=new CompoundTag(); tag.putInt("slot",i+1); tag.putString("name",stack.getHoverName().getString());
                tag.putInt("wave",lane.pearl.isEmpty()?0:lane.wave+1); tag.putString("error",lane.error.isEmpty()?lane.waiting:lane.error);
                tag.putInt("progress",lane.total==0?0:(int)Math.clamp(100L*(lane.total-lane.remaining)/lane.total,0,100));lanes.add(tag);
            }
            status.put("lanes",lanes); data.put("simulator",status);
        }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getServerData().contains("simulator")) return;
            var data=accessor.getServerData().getCompound("simulator");
            tooltip.add(Component.literal(data.getBoolean("online")?"Device Online":"Device Offline"));
            if (data.getBoolean("paused")) tooltip.add(Component.literal("Paused"));
            tooltip.add(Component.literal(data.getBoolean("adjacentOutput") ? "Output: Adjacent Containers" : "Output: ME Network"));
            if (data.getBoolean("blocked")) tooltip.add(Component.literal(data.getBoolean("adjacentOutput")
                    ? "Waiting for selected item / XP-fluid containers" : "Waiting for ME item / XP-fluid storage"));
            tooltip.add(Component.literal("Simulation slots: "+data.getInt("capacity")));
            for (var raw:data.getList("lanes",10)) {
                var lane=(CompoundTag)raw;
                String state=!lane.getString("error").isEmpty()?lane.getString("error")
                        :(lane.getInt("wave")>0?"Wave "+lane.getInt("wave")+", ":"")+lane.getInt("progress")+"%";
                tooltip.add(Component.literal(lane.getInt("slot")+": "+lane.getString("name")+" - "+state));
            }
        }
    }
}
