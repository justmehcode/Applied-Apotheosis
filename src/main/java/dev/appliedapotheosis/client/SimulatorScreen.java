package dev.appliedapotheosis.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.IconButton;
import dev.appliedapotheosis.simulator.SimulatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SimulatorScreen extends AEBaseScreen<SimulatorMenu> {
    private final IconButton pause;
    private final appeng.client.gui.widgets.SettingToggleButton<appeng.api.config.YesNo> output;
    private final IconButton sides;
    public SimulatorScreen(SimulatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, StyleManager.loadStyleDoc("/screens/applied_apotheosis/simulator.json"));
        widgets.add("upgrades", new appeng.client.gui.widgets.UpgradesPanel(
                menu.getSlots(appeng.menu.SlotSemantics.UPGRADE), () -> {
                    var lines = new java.util.ArrayList<Component>();
                    lines.add(appeng.core.localization.GuiText.CompatibleUpgrades.text());
                    lines.addAll(appeng.api.upgrades.Upgrades.getTooltipLinesForMachine(menu.capacity() == 8
                            ? dev.appliedapotheosis.AppliedApotheosis.EXTENDED_SIMULATOR_ITEM.get() : dev.appliedapotheosis.AppliedApotheosis.SIMULATOR_ITEM.get()));
                    return lines;
                }));
        pause = addToLeftToolbar(new IconButton(b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SimulatorMenu.PAUSE)) {
            @Override protected Icon getIcon() { return menu.paused() ? Icon.REDSTONE_OFF : Icon.REDSTONE_ON; }
        });
        pause.setMessage(Component.literal("Pause / resume"));
        var stop = addToLeftToolbar(new IconButton(b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SimulatorMenu.STOP)) {
            @Override protected Icon getIcon() { return Icon.CLEAR; }
        });
        stop.setMessage(Component.literal("Stop gateway runs"));
        stop.setTooltip(Tooltip.create(Component.literal("Stop all gateway runs. Consumed pearls are not returned. Saved rewards still export.")));
        output = addToLeftToolbar(new appeng.client.gui.widgets.SettingToggleButton<>(
                appeng.api.config.Settings.AUTO_EXPORT, appeng.api.config.YesNo.NO,
                (button, backwards) -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SimulatorMenu.OUTPUT_MODE)) {
            @Override public java.util.List<Component> getTooltipMessage() {
                return java.util.List.of(Component.literal(menu.adjacentOutput() ? "Output: Adjacent Containers" : "Output: ME Network"),
                        Component.literal("Click to switch destination. Accepts ME power or FE."));
            }
        });
        sides = addToLeftToolbar(new IconButton(b -> switchToScreen(new SimulatorOutputScreen(this))) {
            @Override protected Icon getIcon() { return Icon.COG; }
            @Override public void renderWidget(GuiGraphics g, int mx, int my, float partial) {
                if (!net.neoforged.fml.ModList.get().isLoaded("extendedae")) { super.renderWidget(g,mx,my,partial); return; }
                if (!visible) return;
                int offset = isHovered() ? 1 : 0;
                (isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND).getBlitter()
                        .dest(getX()-1,getY()+offset,18,20).zOffset(2).blit(g);
                // Same installed sprite used by ExtendedAE's Circuit Slicer output-sides button.
                appeng.client.gui.style.Blitter.texture(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("extendedae","textures/guis/nicons.png"),64,64)
                        .src(0,16,16,16).dest(getX(),getY()+1+offset).zOffset(3).blit(g);
            }
        });
        sides.setMessage(Component.literal("Configure output sides"));
    }
    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        setTextContent(TEXT_ID_DIALOG_TITLE, Component.literal(menu.capacity() == 8 ? "Extended ME Simulator" : "ME Simulator"));
        pause.setTooltip(Tooltip.create(Component.literal(menu.paused() ? "Resume simulations" : "Pause simulations")));
        output.set(menu.adjacentOutput() ? appeng.api.config.YesNo.YES : appeng.api.config.YesNo.NO);
        sides.setVisibility(menu.adjacentOutput());
    }
    @Override public void drawBG(GuiGraphics g, int x, int y, int mouseX, int mouseY, float partial) {
        super.drawBG(g,x,y,mouseX,mouseY,partial);
        if (menu.capacity() == 8) g.fill(x+8,y+18,x+168,y+19,0xFF000000 | ClientSetup.ENTRO_COLOR);
        for (var slot : menu.slots) if (slot.index >= menu.capacity() && !menu.getSlots(appeng.menu.SlotSemantics.UPGRADE).contains(slot)) Icon.SLOT_BACKGROUND.getBlitter().dest(x+slot.x-1,y+slot.y-1).blit(g);
        // Use the actual ME Drive's two-column slot frame from the installed AE2 texture.
        for (int row=0; row<menu.capacity()/2; row++) {
            var slot = menu.getSlot(row*2);
            appeng.client.gui.style.Blitter.texture("guis/drive.png").src(70,13,36,18)
                    .dest(x+slot.x-1,y+slot.y-1).blit(g);
        }
        for (int i=0; i<menu.capacity(); i++) {
            var slot = menu.getSlot(i);
            if (!slot.hasItem()) drawHolder(g, x+slot.x, y+slot.y,
                    net.neoforged.fml.ModList.get().isLoaded("gateways") && (net.minecraft.Util.getMillis()/2000)%2 == 1);
        }
        if (menu.paused()) g.drawString(font,"Paused",x+8,y+114,0x404050,false);
        else if (menu.blocked()) g.drawString(font,menu.adjacentOutput()?
                (menu.waitingForXpOnly()?"Waiting for XP-fluid tank":"Waiting for containers"):"Waiting for ME storage",x+8,y+114,0x404050,false);
    }
    @Override protected void renderTooltip(GuiGraphics g, int mx, int my) {
        if (hoveredSlot == null || !menu.getCarried().isEmpty()) { super.renderTooltip(g,mx,my); return; }
        int index = hoveredSlot.index;
        if (index >= menu.capacity() && index != menu.capacity()+4) { super.renderTooltip(g,mx,my); return; }
        var stack = hoveredSlot.getItem();
        var lines = stack.isEmpty() ? new java.util.ArrayList<Component>() : new java.util.ArrayList<>(getTooltipFromContainerItem(stack));
        if (index == menu.capacity()+4) lines.add(Component.literal("Sword: supplies enchantments to simulated mob loot"));
        else if (stack.isEmpty()) lines.add(Component.literal(net.neoforged.fml.ModList.get().isLoaded("gateways") ? "Spawner or Gate Pearl" : "Spawner"));
        else {
            var messages = menu.laneMessages.split("\n", -1);
            if (index < messages.length && !messages[index].isEmpty()) lines.add(Component.literal(messages[index]));
            else lines.add(Component.literal((menu.laneState(index)==2 ? "Wave "+menu.wave(index)+" - " : "")+menu.progress(index)+"%"));
            if (menu.laneState(index)==2) lines.add(Component.literal("Pearl consumed; use Stop to end this run"));
        }
        g.renderTooltip(font, lines, stack.getTooltipImage(), stack, mx, my);
    }
    /** Small code-drawn placeholder silhouettes, using the same light/shadow treatment as empty slots. */
    private static void drawHolder(GuiGraphics g, int x, int y, boolean pearl) {
        String[] shape = pearl ? new String[]{
            "     ######     ", "   ##      ##   ", "  #          #  ", " #            # ",
            " #  ##        # ", "#  #           #", "#  #           #", "#              #",
            "#              #", "#              #", "#              #", " #            # ",
            " #            # ", "  #          #  ", "   ##      ##   ", "     ######     "
        } : new String[]{
            "                ", "  ############  ", "  #   #  #   #  ", "  #   #  #   #  ",
            "  #   #  #   #  ", "  ############  ", "  #   #  #   #  ", "  #   #  #   #  ",
            "  #   #  #   #  ", "  ############  ", "  #   #  #   #  ", "  #   #  #   #  ",
            "  #   #  #   #  ", "  ############  ", "                ", "                "
        };
        for (int row=0;row<16;row++) for(int col=0;col<16;col++) if(shape[row].charAt(col)=='#')
            g.fill(x+col,y+row,x+col+1,y+row+1,0xFF9999A8);
    }
}
