package dev.appliedapotheosis.simulator;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Copies the entire weapon, including native enchantments; never mutates the installed sword. */
public final class SimulationWeapon implements AutoCloseable {
    private final Player player;
    private final ItemStack previous;
    public SimulationWeapon(Player player, ItemStack sword) {
        this.player = player; previous = player.getMainHandItem();
        player.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
    }
    @Override public void close() { player.setItemSlot(EquipmentSlot.MAINHAND, previous); }
}
