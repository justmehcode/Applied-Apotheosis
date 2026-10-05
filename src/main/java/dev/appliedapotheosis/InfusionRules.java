package dev.appliedapotheosis;

import dev.shadowsoffire.apothic_enchanting.table.infusion.InfusionRecipe;
import dev.shadowsoffire.apothic_enchanting.util.MiscUtil;

public final class InfusionRules {
    public static final int LAPIS = 3;
    public static final int MB_PER_XP = 20;
    public static final int DURATION = 100;
    private InfusionRules() {}
    public static int selectedStat(float minimum) { return Math.max(0, (int) Math.ceil(minimum)); }
    public static boolean validStat(float minimum, float maximum) {
        int selected = selectedStat(minimum);
        return Float.isFinite(minimum) && Float.isFinite(maximum) && selected <= 100
                && (maximum < 0 || selected <= maximum);
    }
    public static boolean supported(InfusionRecipe recipe) {
        var min = recipe.getRequirements(); var max = recipe.getMaxRequirements();
        return validStat(min.eterna(), max.eterna()) && validStat(min.quanta(), max.quanta())
                && validStat(min.arcana(), max.arcana());
    }
    public static int xpCost(InfusionRecipe recipe) {
        return MiscUtil.getExpCostForSlot(Math.max(3, selectedStat(recipe.getRequirements().eterna())), 2);
    }
    public static long fluidCost(InfusionRecipe recipe) { return (long) xpCost(recipe) * MB_PER_XP; }
}
