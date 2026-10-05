package dev.appliedapotheosis.simulator;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import dev.appliedapotheosis.AppliedApotheosis;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.*;

public final class SimulatorMenu extends AEBaseMenu {
    public static final int PAUSE = 0, STOP = 1, OUTPUT_MODE = 2, CLEAR_OUTPUT_SIDES = 3, OUTPUT_SIDE = 10;
    @appeng.menu.guisync.GuiSync(50)
    public String laneMessages = "";
    private final SimulatorBlockEntity machine;
    private final int capacity;
    private final ContainerData data = new SimpleContainerData(31);
    private net.minecraft.core.BlockPos machinePos;
    public static SimulatorMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        var pos = buf.readBlockPos(); var menu = new SimulatorMenu(id, inv, null, buf.readVarInt());
        menu.machinePos = pos; return menu;
    }
    public SimulatorMenu(int id, Inventory player, SimulatorBlockEntity machine, int capacity) {
        super(AppliedApotheosis.SIMULATOR_MENU.get(), id, player, machine);
        if (capacity != 4 && capacity != 8) throw new IllegalArgumentException("Invalid simulator capacity");
        this.machine = machine; this.capacity = capacity;
        this.machinePos = machine == null ? net.minecraft.core.BlockPos.ZERO : machine.getBlockPos();
        var client = new ItemStackHandler(capacity);
        var view = new IItemHandlerModifiable() {
            @Override public int getSlots() { return capacity; }
            @Override public ItemStack getStackInSlot(int slot) {
                if (machine == null) return client.getStackInSlot(slot);
                var pearl = machine.lanes[slot].pearl;
                return pearl.isEmpty() ? machine.inventory.getStackInSlot(slot) : pearl;
            }
            @Override public void setStackInSlot(int slot, ItemStack stack) {
                if (machine == null) client.setStackInSlot(slot, stack);
                else if (machine.lanes[slot].pearl.isEmpty()) machine.inventory.setStackInSlot(slot, stack);
            }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return editable(slot) && SimulatorIntegration.accepts(stack); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!isItemValid(slot, stack)) return stack;
                return (machine == null ? client : machine.inventory).insertItem(slot, stack, simulate);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return editable(slot) ? (machine == null ? client : machine.inventory).extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
        };
        for (int i=0; i<capacity; i++) {
            final int lane = i;
            addSlot(new SlotItemHandler(view, i, 70+(i%2)*18, 28+(i/2)*18) {
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPickup(Player p) { return editable(lane); }
                @Override public boolean mayPlace(ItemStack stack) { return view.isItemValid(lane, stack); }
            }, SlotSemantics.STORAGE_CELL);
        }
        var cards = new appeng.api.inventories.PlatformInventoryWrapper(machine == null ? new ItemStackHandler(4) : machine.upgrades);
        for (int i=0; i<4; i++) {
            var slot = new appeng.menu.slot.AppEngSlot(cards, i) {
                @Override public boolean mayPlace(ItemStack stack) { return stack.is(appeng.core.definitions.AEItems.SPEED_CARD.asItem()); }
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPickup(Player p) { return true; }
            };
            slot.setIcon(appeng.client.gui.Icon.BACKGROUND_UPGRADE);
            addSlot(slot, SlotSemantics.UPGRADE);
        }
        addSlot(new SlotItemHandler(machine == null ? new ItemStackHandler(1) : machine.sword, 0, 134, 55) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(net.minecraft.tags.ItemTags.SWORDS); }
            @Override public com.mojang.datafixers.util.Pair<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> getNoItemIcon() {
                return com.mojang.datafixers.util.Pair.of(InventoryMenu.BLOCK_ATLAS, net.minecraft.resources.ResourceLocation.withDefaultNamespace("item/empty_slot_sword"));
            }
        });
        for (int row=0; row<3; row++) for (int col=0; col<9; col++)
            addSlot(new Slot(player, col+row*9+9, 8+col*18, 136+row*18), SlotSemantics.PLAYER_INVENTORY);
        for (int col=0; col<9; col++) addSlot(new Slot(player, col, 8+col*18, 194), SlotSemantics.PLAYER_HOTBAR);
        addDataSlots(data);
    }
    private boolean editable(int lane) { return machine == null ? (data.get(28) & (1 << lane)) == 0 : machine.lanes[lane].pearl.isEmpty(); }
    public int capacity() { return capacity; }
    public int laneState(int lane) { return data.get(4+lane); }
    public int progress(int lane) { return data.get(12+lane); }
    public int wave(int lane) { return data.get(20+lane); }
    public boolean online() { return data.get(0) == 1; }
    public boolean paused() { return data.get(1) == 1; }
    public boolean blocked() { return data.get(2) == 1; }
    public boolean waitingForXpOnly() { return data.get(3) == 2; }
    public boolean adjacentOutput() { return data.get(29) == 1; }
    public boolean outputEnabled(appeng.api.orientation.RelativeSide side) { return (data.get(30) & (1 << side.ordinal())) != 0; }
    public net.minecraft.core.BlockPos machinePos() { return machinePos; }
    @Override public void broadcastChanges() {
        if (machine != null) {
            data.set(0, machine.node().isActive()?1:0); data.set(1, machine.paused?1:0); data.set(2, machine.blocked()?1:0);
            data.set(3, (machine.pending.isEmpty()?0:1) | (machine.pendingXpFluid>0?2:0));
            data.set(29, machine.adjacentOutput()?1:0); data.set(30, machine.outputSides());
            int committed = 0;
            var messages = new java.util.ArrayList<String>();
            for (int i=0; i<capacity; i++) {
                var lane = machine.lanes[i];
                messages.add((lane.error.isEmpty()?lane.waiting:lane.error).replace('\n',' '));
                if (!lane.pearl.isEmpty()) committed |= 1 << i;
                data.set(4+i, (!lane.error.isEmpty() || !lane.waiting.isEmpty())?3:!lane.pearl.isEmpty()?2:!machine.inventory.getStackInSlot(i).isEmpty()?1:0);
                data.set(12+i, lane.total == 0 ? 0 : (int)Math.clamp(100L*(lane.total-lane.remaining)/lane.total, 0, 100));
                data.set(20+i, Math.min(32767, lane.wave+1));
            }
            data.set(28, committed);
            laneMessages = String.join("\n", messages);
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (machine == null || !stillValid(player)) return false;
        if (button == PAUSE) machine.togglePause();
        else if (button == STOP) machine.stopGateways();
        else if (button == OUTPUT_MODE) machine.toggleOutputMode();
        else if (button == CLEAR_OUTPUT_SIDES) machine.clearOutputSides();
        else if (button >= OUTPUT_SIDE && button < OUTPUT_SIDE + appeng.api.orientation.RelativeSide.values().length)
            machine.toggleOutputSide(appeng.api.orientation.RelativeSide.values()[button - OUTPUT_SIDE]);
        else return false;
        broadcastChanges(); return true;
    }
    @Override public boolean stillValid(Player player) {
        return machine == null || !machine.isRemoved() && machine.getLevel().getBlockEntity(machine.getBlockPos()) == machine
                && player.distanceToSqr(machine.getBlockPos().getCenter()) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var stack = slot.getItem(); var original = stack.copy();
        if (index < capacity+5) { if (!moveItemStackTo(stack, capacity+5, slots.size(), true)) return ItemStack.EMPTY; }
        else {
            int start = stack.is(net.minecraft.tags.ItemTags.SWORDS) ? capacity+4 : stack.is(appeng.core.definitions.AEItems.SPEED_CARD.asItem()) ? capacity : 0;
            int end = start == capacity+4 ? capacity+5 : start == capacity ? capacity+4 : capacity;
            if (!moveItemStackTo(stack, start, end, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return original;
    }
}
