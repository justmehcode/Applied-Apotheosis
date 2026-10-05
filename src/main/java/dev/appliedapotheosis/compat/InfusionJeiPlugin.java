package dev.appliedapotheosis.compat;

import dev.appliedapotheosis.*;
import dev.appliedapotheosis.client.ControllerScreen;
import dev.appliedapotheosis.client.SimulatorScreen;
import dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentScreen;
import net.minecraft.client.renderer.Rect2i;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import dev.shadowsoffire.apothic_enchanting.Ench;
import dev.shadowsoffire.apothic_enchanting.table.infusion.InfusionRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

@JeiPlugin
public final class InfusionJeiPlugin implements IModPlugin {
    static final RecipeType<InfusionRecipe> TYPE = RecipeType.create(AppliedApotheosis.ID, "infusion", InfusionRecipe.class);
    @Override public ResourceLocation getPluginUid() { return AppliedApotheosis.id("jei"); }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) registration.addRecipes(TYPE, level.getRecipeManager().getAllRecipesFor(Ench.RecipeTypes.INFUSION)
                .stream().map(RecipeHolder::value).filter(InfusionRules::supported).toList());
        for (var item : java.util.List.of(AppliedApotheosis.CONTROLLER_ITEM, AppliedApotheosis.EXTENDED_ITEM))
            registration.addIngredientInfo(new ItemStack(item.get()), VanillaTypes.ITEM_STACK,
                Component.literal("The regular controller processes one job; the extended controller processes four at once. Each Acceleration Card adds 1x speed, up to 5x with four cards. Cards can be removed while processing. For automation, place next to a powered AE2 Pattern Provider and encode 1 infusion input + 3 lapis -> the exact output. Put the pattern in the provider, without XP fluid. Provider jobs use stored Just Dire Things XP fluid at 20 mB/point. Manual infusion starts automatically when you insert the item and lapis. The left XP toggle selects personal or ME XP for manual work. Remove an input to cancel and refund its XP payment. Left arrows switch processing slots. When several manual recipes match, the first recipe by ID is selected; use a processing pattern for a specific alternative. No adjacent enchanting table required."));
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(AppliedApotheosis.CONTROLLER_ITEM.get()), TYPE);
        registration.addRecipeCatalyst(new ItemStack(AppliedApotheosis.EXTENDED_ITEM.get()), TYPE);
    }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(SimulatorScreen.class, new IGuiContainerHandler<SimulatorScreen>() {
            @Override public java.util.List<Rect2i> getGuiExtraAreas(SimulatorScreen screen) { return screen.getExclusionZones(); }
        });
        registration.addGuiContainerHandler(ControllerScreen.class, new IGuiContainerHandler<ControllerScreen>() {
            @Override public java.util.List<Rect2i> getGuiExtraAreas(ControllerScreen screen) {
                return screen.getExclusionZones();
            }
        });
    }
    private static final class Category implements IRecipeCategory<InfusionRecipe> {
        private final IDrawable background, icon;
        Category(IGuiHelper helper) {
            background = helper.createBlankDrawable(174, 96);
            icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(AppliedApotheosis.CONTROLLER_ITEM.get()));
        }
        @Override public RecipeType<InfusionRecipe> getRecipeType() { return TYPE; }
        @Override public Component getTitle() { return Component.literal("ME Infusion"); }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, InfusionRecipe recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 6, 6).addIngredients(recipe.getInput());
            builder.addSlot(RecipeIngredientRole.INPUT, 35, 6).addItemStack(new ItemStack(Items.LAPIS_LAZULI, 3));
            builder.addSlot(RecipeIngredientRole.OUTPUT, 146, 6).addItemStack(recipe.getOutput());
        }
        @Override public void draw(InfusionRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double x, double y) {
            var font = Minecraft.getInstance().font; var stats = recipe.getRequirements();
            g.blit(ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/inscriber.png"), 85, 7, 81, 40, 24, 14, 256, 256);
            g.drawString(font, "ME XP: " + InfusionRules.fluidCost(recipe) + " mB", 6, 32, 0xFF8056A7, false);
            g.drawString(font, "(" + InfusionRules.xpCost(recipe) + " XP points, 5 seconds)", 6, 44, 0xFF555555, false);
            int[] values = {InfusionRules.selectedStat(stats.eterna()), InfusionRules.selectedStat(stats.quanta()), InfusionRules.selectedStat(stats.arcana())};
            int[] colors = {0x267C32, 0xBD3939, 0x8C299E};
            String[] names = {"Eterna", "Quanta", "Arcana"};
            for (int row=0; row<3; row++) {
                int yBar = 59+12*row;
                g.drawString(font, names[row], 6, yBar, colors[row], false);
                g.blit(ApothEnchantmentScreen.TEXTURES, 58, yBar+1, 59, 75+10*row, 110, 5, 256, 256);
                int length = Math.clamp(ApothEnchantmentScreen.getBarLength(values[row]), 0, 110);
                if (length > 0) g.blit(ApothEnchantmentScreen.TEXTURES, 58, yBar+1, 0, 197+5*row, length, 5, 256, 256);
                g.drawString(font, Integer.toString(values[row]), 154, yBar, 0xFFFFFF, true);
            }
        }
    }
}
