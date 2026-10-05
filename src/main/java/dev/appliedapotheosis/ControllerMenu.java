package dev.appliedapotheosis;

import appeng.core.definitions.AEItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.*;

public final class ControllerMenu extends appeng.menu.AEBaseMenu {
    public static final int NEXT = 0, PREVIOUS = 1, TOGGLE_XP = 2;
    private final ControllerBlockEntity controller;
    private final Player player;
    private final ContainerData data = new SimpleContainerData(18);
    private final ItemStackHandler clientInventory = new ItemStackHandler(3);
    private final ItemStackHandler preview = new ItemStackHandler(1);
    private final ItemStackHandler inputPreview = new ItemStackHandler(1);
    private final IItemHandler visible;
    private ItemStack previousInput = ItemStack.EMPTY;
    public static ControllerMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos(); return new ControllerMenu(id, inventory, null);
    }
    public ControllerMenu(int id, Inventory inventory, ControllerBlockEntity controller) {
        super(AppliedApotheosis.CONTROLLER_MENU.get(), id, inventory, controller);
        this.controller = controller; this.player = inventory.player;
        data.set(1, controller == null ? 1 : controller.pages());
        data.set(2, controller == null ? 1 : controller.capacity()); data.set(6, -2);
        ItemStackHandler cards = controller == null ? new ItemStackHandler(4) : controller.upgrades;
        var cardInventory = new appeng.api.inventories.PlatformInventoryWrapper(cards);
        for (int i=0; i<4; i++) {
            var slot = new appeng.menu.slot.AppEngSlot(cardInventory, i) {
                @Override public boolean mayPlace(ItemStack stack) { return stack.is(AEItems.SPEED_CARD.asItem()); }
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPickup(Player p) { return true; }
            };
            slot.setIcon(appeng.client.gui.Icon.BACKGROUND_UPGRADE);
            addSlot(slot, appeng.menu.SlotSemantics.UPGRADE);
        }
        visible = new IItemHandlerModifiable() {
            private int index(int slot) { return page()*3+slot; }
            private ItemStackHandler handler() { return controller == null ? clientInventory : controller.inventory; }
            private int slotIndex(int slot) { return controller == null ? slot : index(slot); }
            @Override public int getSlots() { return 3; }
            @Override public ItemStack getStackInSlot(int slot) { return handler().getStackInSlot(slotIndex(slot)); }
            @Override public void setStackInSlot(int slot, ItemStack stack) {
                if (controller != null && slot < 2) controller.editInputs(page(), player);
                handler().setStackInSlot(slotIndex(slot), stack);
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isItemValid(slot, stack) ? handler().insertItem(slotIndex(slot), stack, simulate) : stack;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot != 2 && !canEditInputs()) return ItemStack.EMPTY;
                if (controller != null && slot < 2 && !simulate) controller.editInputs(page(), player);
                return handler().extractItem(slotIndex(slot), amount, simulate);
            }
            @Override public int getSlotLimit(int slot) { return 64; }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return canEditInputs() && (slot == 0 || slot == 1 && stack.is(Items.LAPIS_LAZULI));
            }
        };
        addSlot(new SlotItemHandler(visible, 0, 39, 23) {
            @Override public void setChanged() { if (controller != null) controller.editInputs(page(), player); super.setChanged(); }
            @Override public boolean mayPickup(Player p) { return canEditInputs(); }
            @Override public boolean mayPlace(ItemStack stack) { return canEditInputs(); }
        }, appeng.menu.SlotSemantics.INSCRIBER_PLATE_TOP);
        addSlot(new SlotItemHandler(visible, 1, 39, 55) {
            @Override public com.mojang.datafixers.util.Pair<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> getNoItemIcon() {
                return com.mojang.datafixers.util.Pair.of(InventoryMenu.BLOCK_ATLAS,
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("item/empty_slot_lapis_lazuli"));
            }
            @Override public void setChanged() { if (controller != null) controller.editInputs(page(), player); super.setChanged(); }
            @Override public boolean mayPickup(Player p) { return canEditInputs(); }
            @Override public boolean mayPlace(ItemStack stack) { return canEditInputs() && stack.is(Items.LAPIS_LAZULI); }
        }, appeng.menu.SlotSemantics.INSCRIBER_PLATE_BOTTOM);
        addSlot(new SlotItemHandler(visible, 2, 113, 40) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        }, appeng.menu.SlotSemantics.MACHINE_OUTPUT);
        addSlot(new SlotItemHandler(preview, 0, -1000, -1000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player p) { return false; }
            @Override public boolean isActive() { return false; }
        });
        addSlot(new SlotItemHandler(inputPreview, 0, -1000, -1000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player p) { return false; }
            @Override public boolean isActive() { return false; }
        });
        for (int row=0; row<3; row++) for (int col=0; col<9; col++)
            addSlot(new Slot(inventory, col+row*9+9, 8+col*18, 160+row*18), appeng.menu.SlotSemantics.PLAYER_INVENTORY);
        for (int col=0; col<9; col++) addSlot(new Slot(inventory, col, 8+col*18, 218), appeng.menu.SlotSemantics.PLAYER_HOTBAR);
        addDataSlots(data);
    }
    private boolean canEditInputs() { return controller == null || controller.canEditInputs(page()); }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (controller == null || !stillValid(player)) return false;
        if (button == NEXT || button == PREVIOUS) {
            data.set(0, Math.floorMod(page() + (button == NEXT ? 1 : -1), controller.pages())); data.set(14, 0);
        } else if (button == TOGGLE_XP) {
            controller.toggleXpSource();
        } else return false;
        broadcastChanges(); return true;
    }
    @Override public void broadcastChanges() {
        if (controller != null) {
            data.set(1, controller.pages());
            if (page() >= pages()) data.set(0, 0);
            data.set(2, controller.capacity()); data.set(3, controller.speed()); data.set(4, controller.jobCount());
            var job = controller.jobs[page()]; data.set(6, job == null ? -2 : job.remaining);
            data.set(17, controller.usesPlayerXp() ? 1 : 0);
            data.set(5, (job == null ? controller.usesPlayerXp() : job.personalPayer != null) ? 0 : controller.node().isActive() ? 1 : 2);
            long points = usesPlayerXp() ? ExperienceMath.totalAtLevel(player.experienceLevel)
                    + Math.round(player.experienceProgress * player.getXpNeededForNextLevel()) : controller.availableXpFluid()/20;
            points = Math.min(Integer.MAX_VALUE, points);
            data.set(11, (int) points & 65535); data.set(12, (int) (points >>> 16));
            var input = controller.inventory.getStackInSlot(page()*3);
            if (!ItemStack.isSameItemSameComponents(previousInput, input)) data.set(14, 0);
            previousInput = input.copy();
            var recipes = controller.recipes(page()); data.set(13, recipes.size());
            if (data.get(14) >= recipes.size()) data.set(14, 0);
            if (job != null) {
                data.set(7, job.eterna); data.set(8, job.quanta); data.set(9, job.arcana); data.set(10, (int) (job.cost/20));
                preview.setStackInSlot(0, job.output.copy()); inputPreview.setStackInSlot(0, job.preview.copy());
            } else if (!recipes.isEmpty()) {
                var recipe = recipes.get(data.get(14)).value(); var stats = recipe.getRequirements();
                data.set(7, InfusionRules.selectedStat(stats.eterna())); data.set(8, InfusionRules.selectedStat(stats.quanta()));
                data.set(9, InfusionRules.selectedStat(stats.arcana())); data.set(10, InfusionRules.xpCost(recipe));
                preview.setStackInSlot(0, recipe.assemble(input.copyWithCount(1), eterna(), quanta(), arcana()));
                inputPreview.setStackInSlot(0, ItemStack.EMPTY);
            } else {
                for (int i=7; i<=10; i++) data.set(i, 0);
                preview.setStackInSlot(0, ItemStack.EMPTY); inputPreview.setStackInSlot(0, ItemStack.EMPTY);
            }
            boolean lapis = controller.inventory.getStackInSlot(page()*3+1).getCount() >= 3;
            boolean xp = points >= xpCost() || usesPlayerXp() && player.getAbilities().instabuild;
            var out = controller.inventory.getStackInSlot(page()*3+2); var result = preview();
            boolean space = out.isEmpty() || ItemStack.isSameItemSameComponents(out, result) && out.getCount()+result.getCount() <= out.getMaxStackSize();
            data.set(15, job == null && !recipes.isEmpty() && lapis && xp && space && data.get(5) != 2 ? 1 : 0);
            data.set(16, job != null ? (job.remaining == -1 ? 4 : job.remaining == 0 ? 5 : 1)
                    : recipes.isEmpty() ? 2 : !lapis ? 3 : !space ? 5 : !xp ? 4 : 0);
        }
        super.broadcastChanges();
    }
    public int page() { return data.get(0); }
    public int pages() { return data.get(1); }
    public int capacity() { return data.get(2); }
    public int speed() { return Math.max(1, data.get(3)); }
    public int activeJobs() { return data.get(4); }
    public boolean online() { return data.get(5) == 1; }
    public boolean usesPlayerXp() { return data.get(5) == 0; }
    public boolean manualUsesPlayerXp() { return data.get(17) == 1; }
    public int progress() { return data.get(6); }
    public int eterna() { return data.get(7); }
    public int quanta() { return data.get(8); }
    public int arcana() { return data.get(9); }
    public int xpCost() { return data.get(10); }
    public long xpPoints() { return (data.get(11)&65535L) | (data.get(12)&65535L)<<16; }
    public int recipeCount() { return data.get(13); }
    public int recipeIndex() { return data.get(14); }
    public boolean canStart() { return data.get(15) == 1; }
    public int status() { return data.get(16); }
    public ItemStack preview() { return preview.getStackInSlot(0); }
    public ItemStack inputPreview() { return inputPreview.getStackInSlot(0); }
    @Override public boolean stillValid(Player player) {
        return controller == null || !controller.isRemoved() && controller.getLevel().getBlockEntity(controller.getBlockPos()) == controller
                && player.distanceToSqr(controller.getBlockPos().getCenter()) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem() || !slot.mayPickup(player) || !slot.isActive()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var original = stack.copy();
        if (index < 7) { if (!moveItemStackTo(stack, 9, slots.size(), true)) return ItemStack.EMPTY; }
        else if (index >= 9) {
            int target = stack.is(AEItems.SPEED_CARD.asItem()) ? 0 : stack.is(Items.LAPIS_LAZULI) ? 5 : 4;
            if (!moveItemStackTo(stack, target, target == 0 ? 4 : target+1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return original;
    }
}
