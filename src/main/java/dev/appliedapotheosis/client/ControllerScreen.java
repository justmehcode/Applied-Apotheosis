package dev.appliedapotheosis.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import appeng.core.definitions.AEItems;
import appeng.api.upgrades.Upgrades;
import appeng.core.localization.GuiText;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.appliedapotheosis.*;
import dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Uses installed AE2 widgets/textures and Apothic's stat-bar rendering dimensions. */
public final class ControllerScreen extends appeng.client.gui.AEBaseScreen<ControllerMenu> {
    private static final ResourceLocation ORB = ResourceLocation.withDefaultNamespace("textures/entity/experience_orb.png");
    private static final ResourceLocation XP_BG = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation XP_FILL = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
    private IconButton next, previous, xpSource;
    public ControllerScreen(ControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, appeng.client.gui.style.StyleManager.loadStyleDoc("/screens/applied_apotheosis/controller.json"));
        imageWidth = 176; imageHeight = 244;
        widgets.add("upgrades", new appeng.client.gui.widgets.UpgradesPanel(
                menu.getSlots(appeng.menu.SlotSemantics.UPGRADE), () -> {
                    var lines = new java.util.ArrayList<Component>();
                    lines.add(GuiText.CompatibleUpgrades.text());
                    lines.addAll(Upgrades.getTooltipLinesForMachine(menu.capacity() == 4
                            ? AppliedApotheosis.EXTENDED_ITEM.get() : AppliedApotheosis.CONTROLLER_ITEM.get()));
                    return lines;
                }));
        widgets.add("progressBar", new appeng.client.gui.widgets.ProgressBar(
                new appeng.menu.interfaces.IProgressProvider() {
                    public int getCurrentProgress() { return menu.progress() < 0 ? 0 : 100-menu.progress(); }
                    public int getMaxProgress() { return 100; }
                }, style.getImage("progressBar"), appeng.client.gui.widgets.ProgressBar.Direction.VERTICAL));
        next = tool(Icon.ARROW_RIGHT, "Next processing slot", ControllerMenu.NEXT);
        previous = tool(Icon.ARROW_LEFT, "Previous processing slot", ControllerMenu.PREVIOUS);
        xpSource = tool(Icon.ENTER, "Toggle XP source", ControllerMenu.TOGGLE_XP);
    }
    private IconButton tool(Icon icon, String text, int action) {
        var button = new IconButton(b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action)) {
            @Override protected Icon getIcon() { return icon; }
            @Override protected net.minecraft.world.item.Item getItemOverlay() {
                return action == ControllerMenu.TOGGLE_XP
                        ? menu.manualUsesPlayerXp() ? Items.EXPERIENCE_BOTTLE : AEItems.FLUID_CELL_1K.asItem() : null;
            }
        };
        button.setMessage(Component.literal(text)); button.setTooltip(Tooltip.create(Component.literal(text)));
        return addToLeftToolbar(button);
    }
    private void updateToolbar() {
        boolean extended = menu.capacity() == 4;
        next.visible = previous.visible = extended;
        next.active = previous.active = extended && menu.pages() > 1;
    }
    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        updateToolbar();
        setTextContent(TEXT_ID_DIALOG_TITLE, Component.literal(menu.capacity() == 4 ? "Extended Infusion" : "Infusion Controller"));
        xpSource.setTooltip(Tooltip.create(Component.literal((menu.manualUsesPlayerXp() ? "Personal XP" : "External XP")
                + " — click to switch. Uses ME XP fluids or adjacent Experience Lib storage. Pattern Provider jobs use external XP.")));
    }
    private void slot(GuiGraphics g, int x, int y) {
        Icon.SLOT_BACKGROUND.getBlitter().dest(x-1, y-1).blit(g);
    }
    private void ghost(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        g.fill(x, y, x+16, y+16, 0x66C6C6D0);
    }
    private void orb(GuiGraphics g, int x, int y) {
        RenderSystem.enableBlend(); RenderSystem.setShaderColor(.55F, 1F, .25F, 1F);
        g.blit(ORB, x, y, 8, 8, 0, 0, 16, 16, 64, 64);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    private void statBar(GuiGraphics g, String name, int value, int y, int row, int color) {
        var texture = ApothEnchantmentScreen.TEXTURES;
        g.drawString(font, name, leftPos+8, topPos+y, color, false);
        // The background tracks and gradients are the actual Apothic table sprites.
        g.blit(texture, leftPos+58, topPos+y+1, 59, 75+10*row, 110, 5, 256, 256);
        int width = Math.clamp(ApothEnchantmentScreen.getBarLength(value), 0, 110);
        if (width > 0) g.blit(texture, leftPos+58, topPos+y+1, 0, 197+5*row, width, 5, 256, 256);
    }
    @Override public void drawBG(GuiGraphics g, int offsetX, int offsetY, int mouseX, int mouseY, float partial) {
        super.drawBG(g, offsetX, offsetY, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        if (menu.capacity() == 4) g.fill(x+8, y+17, x+168, y+18, ClientSetup.ENTRO_COLOR | 0xFF000000);
        for (var inventorySlot : menu.slots) {
            if (inventorySlot.index >= 9) slot(g, x+inventorySlot.x, y+inventorySlot.y);
        }
        if (menu.capacity() == 4) g.drawString(font, "Slot " + (menu.page()+1) + " / " + menu.pages(), x+8, y+75, 0x404050, false);
        // The inherited Inscriber background supplies all slot frames and arrows at native size.
        var inputSlot = menu.getSlot(4);
        var outputSlot = menu.getSlot(6);
        if (!inputSlot.hasItem() && menu.inputPreview().isEmpty())
            Icon.BACKGROUND_PLATE.getBlitter().dest(x+inputSlot.x, y+inputSlot.y).blit(g);
        if (!inputSlot.hasItem()) ghost(g, menu.inputPreview(), x+inputSlot.x, y+inputSlot.y);
        if (!outputSlot.hasItem()) ghost(g, menu.preview(), x+outputSlot.x, y+outputSlot.y);
        // Remove the unused third-slot frame; keep the native branches and output arrow.
        style.getImage("centerClear").dest(x+60, y+36).blit(g);
        var connector = style.getImage("verticalConnector");
        connector.dest(x+74, y+36).blit(g);
        connector.dest(x+74, y+44).blit(g);
        connector.copy().srcHeight(6).dest(x+74, y+52).blit(g);
        style.getImage("outputConnector").dest(x+76, y+45).blit(g);
        long xp = menu.xpPoints();
        int xpLevel = ExperienceMath.level(xp);
        String levelLabel = Integer.toString(xpLevel);
        g.drawString(font, levelLabel, x+88-font.width(levelLabel)/2, y+86, 0x80FF20, true);
        g.blitSprite(XP_BG, x+8, y+97, 160, 5);
        int fill = (int) (160*ExperienceMath.progress(xp));
        if (fill > 0) {
            g.enableScissor(x+8, y+97, x+8+fill, y+102);
            g.blitSprite(XP_FILL, x+8, y+97, 160, 5); g.disableScissor();
        }
        orb(g, x+9, y+86);
        g.drawString(font, Integer.toString(menu.xpCost()), x+20, y+86, 0x306C22, false);
        String source = menu.usesPlayerXp() ? "Player XP" : "External XP";
        g.drawString(font, source, x+168-font.width(source), y+86, 0x404050, false);
        statBar(g, "Eterna", menu.eterna(), 109, 0, 0x267C32);
        statBar(g, "Quanta", menu.quanta(), 119, 1, 0xBD3939);
        statBar(g, "Arcana", menu.arcana(), 129, 2, 0x8C299E);
        String status = menu.online() ? "Device Online" : "Device Offline";
        g.drawString(font, status, x+8, y+141, 0x404050, false);
    }
    private void tooltip(GuiGraphics g, int mx, int my, String... text) {
        g.renderTooltip(font, java.util.Arrays.stream(text).map(Component::literal).map(Component::getVisualOrderText).toList(), mx, my);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (isHovering(8, 84, 160, 20, mx, my)) {
            tooltip(g, mx, my, (menu.usesPlayerXp() ? "Your XP: " : "External XP: ")+menu.xpPoints()+" points",
                    "Cost: "+menu.xpCost()+" points ("+menu.xpCost()*20+" mB)",
                    "Level "+ExperienceMath.level(menu.xpPoints()), "Active jobs: "+menu.activeJobs()+" / "+menu.capacity()); return;
        }
        for (int row=0; row<3; row++) if (isHovering(8,109+row*10,160,8,mx,my)) {
            int value = row == 0 ? menu.eterna() : row == 1 ? menu.quanta() : menu.arcana();
            tooltip(g,mx,my, new String[]{"Eterna", "Quanta", "Arcana"}[row]+": "+value,
                    "Automatically set for this infusion"); return;
        }
        if (isHovering(menu.getSlot(6).x,menu.getSlot(6).y,16,16,mx,my) && menu.getSlot(6).getItem().isEmpty() && !menu.preview().isEmpty()) {
            tooltip(g,mx,my, menu.preview().getHoverName().getString(), "Recipe preview; collect after infusion"); return;
        }
    }
}
