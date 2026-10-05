package dev.appliedapotheosis.simulator;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class SimulatorIntegration {
    public static boolean pearl(ItemStack stack) { return ModList.get().isLoaded("gateways") && GatewaySimulation.accepts(stack); }
    public static boolean accepts(ItemStack stack) { return SpawnerSimulation.accepts(stack) || pearl(stack); }
}
