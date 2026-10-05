package dev.appliedapotheosis.client;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.TabButton;
import dev.appliedapotheosis.simulator.SimulatorBlock;
import dev.appliedapotheosis.simulator.SimulatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Native AE2 subscreen with a Circuit Slicer-style relative-side cross. */
public final class SimulatorOutputScreen extends AESubScreen<SimulatorMenu, SimulatorScreen> {
    public SimulatorOutputScreen(SimulatorScreen parent) {
        super(parent, "/screens/applied_apotheosis/simulator_output.json");
        widgets.add("return", new TabButton(Icon.BACK, Component.literal("Return to ME Simulator"), b -> returnToParent()));
        var clear = new IconButton(b -> send(SimulatorMenu.CLEAR_OUTPUT_SIDES)) {
            @Override protected Icon getIcon() { return Icon.CLEAR; }
        };
        clear.setHalfSize(true); clear.setDisableBackground(true);
        clear.setMessage(Component.literal("Disable all output sides")); widgets.add("clear", clear);
        for (var side : RelativeSide.values()) {
            var button = new IconButton(b -> send(SimulatorMenu.OUTPUT_SIDE + side.ordinal())) {
                @Override protected Icon getIcon() { return null; }
                @Override public java.util.List<Component> getTooltipMessage() {
                    var item = neighbor(side);
                    return java.util.List.of(Component.literal(side.name().charAt(0)+side.name().substring(1).toLowerCase(java.util.Locale.ROOT)
                                    + (menu.outputEnabled(side) ? ": Output enabled" : ": Output disabled")),
                            item.isEmpty() ? Component.literal("No adjacent container") : item.getHoverName());
                }
                @Override public void renderWidget(GuiGraphics g, int mx, int my, float partial) {
                    var background = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                            : menu.outputEnabled(side) ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
                    background.getBlitter().dest(getX()-1,getY(),18,20).zOffset(2).blit(g);
                    var stack = neighbor(side);
                    if (!stack.isEmpty()) g.renderItem(stack,getX(),getY()+1,0,3);
                }
            };
            button.setMessage(Component.literal(side.name()));
            widgets.add(side.name().toLowerCase(java.util.Locale.ROOT), button);
        }
    }
    private void send(int action) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action); }
    private ItemStack neighbor(RelativeSide side) {
        if (minecraft.level == null) return ItemStack.EMPTY;
        var state = minecraft.level.getBlockState(menu.machinePos());
        var front = state.hasProperty(SimulatorBlock.FACING) ? state.getValue(SimulatorBlock.FACING) : Direction.NORTH;
        var direction = BlockOrientation.get(front, Direction.UP).getSide(side);
        var pos = menu.machinePos().relative(direction);
        if (!minecraft.level.hasChunkAt(pos)) return ItemStack.EMPTY;
        if (minecraft.level.getBlockEntity(pos) instanceof appeng.blockentity.networking.CableBusBlockEntity cable) {
            var part = cable.getPart(direction.getOpposite());
            if (part != null) return new ItemStack(part.getPartItem());
        }
        return new ItemStack(minecraft.level.getBlockState(pos).getBlock());
    }
}
