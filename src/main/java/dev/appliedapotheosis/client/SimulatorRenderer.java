package dev.appliedapotheosis.client;

import appeng.api.orientation.BlockOrientation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.appliedapotheosis.simulator.SimulatorBlock;
import dev.appliedapotheosis.simulator.SimulatorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;

/** Occupied bay faces use the actual item model, including gateway-specific colors. */
public final class SimulatorRenderer implements BlockEntityRenderer<SimulatorBlockEntity> {
    private final ItemRenderer items;
    public SimulatorRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }
    @Override public void render(SimulatorBlockEntity machine, float partial, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(.5,.5,.5);
        pose.mulPose(BlockOrientation.get(machine.getBlockState().getValue(SimulatorBlock.FACING),Direction.UP).getQuaternion());
        pose.translate(-.5,-.5,-.5);
        for (int slot=0; slot<machine.capacity(); slot++) {
            var stack=machine.displayStack(slot);
            if (stack.isEmpty()) continue;
            int bay=slot%4; boolean rear=slot>=4;
            // Both 32px bay textures have opening centers at 9px and 23px,
            // not the quarter points (8px/24px). Mirror U on the north face.
            double u=(9.0+(bay%2)*14.0)/32.0;
            double x=rear ? u : 1.0-u;
            double y=1.0-(9.0+(bay/2)*14.0)/32.0;
            pose.pushPose();
            pose.translate(x,y,rear ? 1.003 : -.003);
            if (!rear) pose.mulPose(Axis.YP.rotationDegrees(180));
            // Flatten the native GUI model into an inset-looking face overlay, not a floating item.
            pose.scale(.30f,.30f,.001f);
            items.renderStatic(stack,ItemDisplayContext.GUI,light,overlay,pose,buffers,machine.getLevel(),slot);
            pose.popPose();
        }
        pose.popPose();
    }
}
