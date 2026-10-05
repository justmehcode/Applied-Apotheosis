package dev.appliedapotheosis.simulator;

import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.*;
import appeng.api.networking.security.*;
import appeng.api.stacks.*;
import dev.appliedapotheosis.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;

public final class SimulatorBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost, MenuProvider {
    public static final int MAX_SLOTS = 8;
    private IManagedGridNode node;
    private CompoundTag nodeData = new CompoundTag();
    private UUID owner;
    private UUID simulationId = UUID.randomUUID();
    private CompoundTag ownerContext = new CompoundTag();
    public final ItemStackHandler sword = new ItemStackHandler(1) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(net.minecraft.tags.ItemTags.SWORDS); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final IActionSource source = IActionSource.ofMachine(this);
    public final ItemStackHandler inventory = new ItemStackHandler(MAX_SLOTS) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < capacity() && lanes[slot].pearl.isEmpty() && SimulatorIntegration.accepts(stack); }
        @Override protected void onContentsChanged(int slot) { if (!loading && lanes[slot].pearl.isEmpty()) lanes[slot] = new Lane(); setChanged(); }
    };
    public final ItemStackHandler upgrades = new ItemStackHandler(4) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(appeng.core.definitions.AEItems.SPEED_CARD.asItem()); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    public int speed() {
        int speed = 1;
        for (int i=0; i<4; i++) if (upgrades.getStackInSlot(i).is(appeng.core.definitions.AEItems.SPEED_CARD.asItem())) speed++;
        return speed;
    }
    public static final class Lane {
        public ItemStack pearl = ItemStack.EMPTY;
        public int wave, remaining, total;
        public double credit;
        public String error = "", waiting = "";
    }
    public final Lane[] lanes = new Lane[MAX_SLOTS];
    public final List<ItemStack> pending = new ArrayList<>();
    public long pendingXpFluid;
    public boolean paused;
    private boolean adjacentOutput;
    // Relative sides keep their meaning when the block is rotated. Default to rear output.
    private int outputSides = 1 << appeng.api.orientation.RelativeSide.BACK.ordinal();
    public boolean adjacentOutput() { return adjacentOutput; }
    public int outputSides() { return outputSides; }
    public void toggleOutputMode() { adjacentOutput = !adjacentOutput; setChanged(); }
    public void toggleOutputSide(appeng.api.orientation.RelativeSide side) { outputSides ^= 1 << side.ordinal(); setChanged(); }
    public void clearOutputSides() { outputSides = 0; setChanged(); }
    public Direction outputDirection(appeng.api.orientation.RelativeSide side) {
        return appeng.api.orientation.BlockOrientation.get(getBlockState().getValue(SimulatorBlock.FACING), Direction.UP).getSide(side);
    }
    private boolean loading;
    private int cursor;
    private int storedFe;
    private final ItemStack[] displayStacks = new ItemStack[MAX_SLOTS];
    private final ItemStack[] lastSentDisplay = new ItemStack[MAX_SLOTS];
    private ItemStack visibleStack(int slot) { return lanes[slot].pearl.isEmpty() ? inventory.getStackInSlot(slot) : lanes[slot].pearl; }
    public ItemStack displayStack(int slot) { return slot < 0 || slot >= capacity() ? ItemStack.EMPTY : displayStacks[slot]; }
    private void syncDisplay() {
        if (level == null || level.isClientSide) return;
        boolean changed = false;
        for (int i=0; i<capacity(); i++) {
            var stack = visibleStack(i);
            if (!ItemStack.matches(lastSentDisplay[i], stack)) { lastSentDisplay[i] = stack.copy(); changed = true; }
        }
        if (changed) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag(); var stacks = new ListTag();
        for (int i=0; i<capacity(); i++) {
            var stack = visibleStack(i);
            stacks.add(stack.isEmpty() ? new CompoundTag() : stack.copyWithCount(1).save(registries));
        }
        tag.put("displayStacks", stacks); return tag;
    }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        var stacks = tag.getList("displayStacks", 10);
        for (int i=0; i<MAX_SLOTS; i++) displayStacks[i] = i < stacks.size() ? ItemStack.parseOptional(registries, stacks.getCompound(i)) : ItemStack.EMPTY;
    }
    @Override public void onDataPacket(net.minecraft.network.Connection connection,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }
    private final net.neoforged.neoforge.energy.IEnergyStorage fe = new net.neoforged.neoforge.energy.IEnergyStorage() {
        @Override public int receiveEnergy(int amount, boolean simulate) {
            int accepted = Math.min(Math.max(0, amount), Math.min(SimulatorConfig.RECEIVE.get(), Math.max(0, getMaxEnergyStored()-getEnergyStored())));
            if (!simulate && accepted > 0) { storedFe = getEnergyStored()+accepted; setChanged(); }
            return accepted;
        }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return Math.min(storedFe, getMaxEnergyStored()); }
        @Override public int getMaxEnergyStored() { return SimulatorConfig.CAPACITY.get(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };
    public net.neoforged.neoforge.energy.IEnergyStorage feStorage() { return fe; }
    public boolean powered() { return node.isActive() || fe.getEnergyStored() > 0; }
    public SimulatorBlockEntity(BlockPos pos, BlockState state) {
        super(AppliedApotheosis.SIMULATOR_ENTITY.get(), pos, state);
        Arrays.setAll(lanes, i -> new Lane()); node = createNode();
        Arrays.fill(displayStacks, ItemStack.EMPTY); Arrays.fill(lastSentDisplay, ItemStack.EMPTY);
    }
    private IManagedGridNode createNode() {
        var n = GridHelper.createManagedNode(this, (SimulatorBlockEntity be, IGridNode changed) -> be.setChanged())
                .setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(4).setInWorldNode(true).setVisualRepresentation(getBlockState().getBlock());
        n.loadFromNBT(nodeData); return n;
    }
    public IManagedGridNode node() { return node; }
    public int capacity() { return getBlockState().is(AppliedApotheosis.EXTENDED_SIMULATOR.get()) ? 8 : 4; }
    public void setOwner(Player player) { owner = player.getUUID(); node.setOwningPlayer(player);
        if (net.neoforged.fml.ModList.get().isLoaded("apotheosis")) ApotheosisSupport.capture(player, ownerContext);
        setChanged(); }
    public void onUserAccess(Player player) {
        if (owner == null) setOwner(player);
        else if (owner.equals(player.getUUID()) && net.neoforged.fml.ModList.get().isLoaded("apotheosis")) {
            ApotheosisSupport.capture(player, ownerContext); setChanged();
        }
    }
    @Override public IGridNode getGridNode(Direction side) { return node.getNode(); }
    @Override public IGridNode getActionableNode() { return node.getNode(); }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) GridHelper.onFirstTick(this, be -> {
            if (be.isRemoved() || be.node.isReady()) return;
            if (be.owner != null && be.level.getPlayerByUUID(be.owner) != null) be.node.setOwningPlayer(be.level.getPlayerByUUID(be.owner));
            be.node.create(be.level, be.worldPosition);
        });
    }
    private void suspendNode() { if (node.isReady()) node.saveToNBT(nodeData); node.destroy(); node = createNode(); }
    @Override public void setRemoved() { suspendNode(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { suspendNode(); super.onChunkUnloaded(); }
    public boolean blocked() { return !pending.isEmpty() || pendingXpFluid > 0; }
    public void stopGateways() {
        for (int i=0; i<capacity(); i++) if (!lanes[i].pearl.isEmpty()) lanes[i] = new Lane();
        setChanged();
    }
    public void togglePause() { paused = !paused; setChanged(); }
    private void flush() {
        if (adjacentOutput) { flushAdjacent(); return; }
        if (!node.isActive() || node.getGrid() == null) return;
        var storage = node.getGrid().getStorageService().getInventory();
        for (var it = pending.iterator(); it.hasNext();) {
            var stack = it.next();
            long inserted = storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, source);
            if (inserted > 0) { stack.shrink((int)inserted); setChanged(); }
            if (stack.isEmpty()) it.remove();
        }
        if (pendingXpFluid > 0 && BuiltInRegistries.FLUID.containsKey(ControllerBlockEntity.XP_FLUID)) {
            var key = AEFluidKey.of(BuiltInRegistries.FLUID.get(ControllerBlockEntity.XP_FLUID));
            long inserted = storage.insert(key, pendingXpFluid, Actionable.MODULATE, source);
            if (inserted > 0) { pendingXpFluid -= inserted; setChanged(); }
        }
    }
    private void flushAdjacent() {
        for (var relative : appeng.api.orientation.RelativeSide.values()) {
            if ((outputSides & (1 << relative.ordinal())) == 0) continue;
            var direction = outputDirection(relative);
            var target = worldPosition.relative(direction);
            if (!level.hasChunkAt(target)) continue;
            var items = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, target, direction.getOpposite());
            if (items != null) for (var it = pending.iterator(); it.hasNext();) {
                var stack = it.next();
                var remainder = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(items, stack.copy(), false);
                int inserted = Math.clamp(stack.getCount() - remainder.getCount(), 0, stack.getCount());
                if (inserted > 0) { stack.shrink(inserted); setChanged(); }
                if (stack.isEmpty()) it.remove();
            }
            if (pendingXpFluid >= 20 && net.neoforged.fml.ModList.get().isLoaded("experiencelib")) {
                long exported = dev.appliedapotheosis.compat.ExperienceLibCompat.export(level, target, direction.getOpposite(), pendingXpFluid);
                if (exported > 0) { pendingXpFluid -= exported; setChanged(); }
            }
            if (pendingXpFluid > 0 && BuiltInRegistries.FLUID.containsKey(ControllerBlockEntity.XP_FLUID)) {
                var fluids = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, target, direction.getOpposite());
                if (fluids != null) {
                    int offered = (int)Math.min(Integer.MAX_VALUE, pendingXpFluid);
                    var fluid = new net.neoforged.neoforge.fluids.FluidStack(BuiltInRegistries.FLUID.get(ControllerBlockEntity.XP_FLUID), offered);
                    int inserted = Math.clamp(fluids.fill(fluid, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE), 0, offered);
                    if (inserted > 0) { pendingXpFluid -= inserted; setChanged(); }
                }
            }
        }
    }
    public void serverTick() {
        for (int step=0, rate=speed(); step<rate; step++) processingTick();
        syncDisplay();
    }
    private void processingTick() {
        if (!(level instanceof ServerLevel server) || !powered()) return;
        flush();
        if (paused || blocked()) return;
        var player = FakePlayerFactory.get(server, new GameProfile(simulationId, "ME_Simulator"));
        boolean knownTier = false;
        if (net.neoforged.fml.ModList.get().isLoaded("apotheosis")) {
            var before = ownerContext.copy();
            knownTier = ApotheosisSupport.refresh(server, owner, ownerContext, player);
            if (!before.equals(ownerContext)) setChanged();
        }
        player.setPos(worldPosition.getCenter());
        int first = cursor;
        for (int step=0; step<capacity(); step++) {
            int i = (first+step)%capacity(); Lane lane = lanes[i];
            if (!lane.error.isEmpty()) continue;
            lane.waiting = "";
            ItemStack input = inventory.getStackInSlot(i);
            if (lane.pearl.isEmpty() && input.isEmpty()) continue;
            try {
                if (lane.pearl.isEmpty() && SimulatorIntegration.pearl(input)) {
                    GatewaySimulation.validate(input);
                    lane.waiting = GatewaySimulation.waitingReason(input, player, knownTier);
                    if (!lane.waiting.isEmpty()) continue;
                    int duration = GatewaySimulation.duration(input, 0);
                    // Debit the first powered tick before committing the consumable.
                    if (!pay(lane, 64)) continue;
                    ItemStack pearl = input.copyWithCount(1);
                    loading = true;
                    try { inventory.extractItem(i, 1, false); } finally { loading = false; }
                    lane.pearl = pearl; lane.total = duration; lane.remaining = duration-1; setChanged();
                    continue;
                }
                boolean gateway = !lane.pearl.isEmpty();
                if (gateway) {
                    lane.waiting = GatewaySimulation.waitingReason(lane.pearl, player, knownTier);
                    if (!lane.waiting.isEmpty()) continue;
                }
                if (!gateway && !SpawnerSimulation.accepts(input)) continue;
                if (!gateway && SpawnerSimulation.data(input).getCompound("stats").getBoolean("apothic_spawners:redstone_control") && !server.hasNeighborSignal(worldPosition)) {
                    lane.waiting = "Spawner requires a redstone signal"; continue;
                }
                if (lane.total == 0) {
                    lane.total = gateway ? GatewaySimulation.duration(lane.pearl, lane.wave) : SpawnerSimulation.delay(input, server);
                    lane.remaining = lane.total;
                }
                if (!pay(lane, gateway ? 64 : 32)) continue;
                if (--lane.remaining > 0) { setChanged(); continue; }
                try (var weapon = new SimulationWeapon(player, sword.getStackInSlot(0)); var loot = new SimulationLoot(server)) {
                    try {
                        if (gateway) GatewaySimulation.runWave(lane.pearl, lane.wave, server, worldPosition, player, loot);
                        else SpawnerSimulation.run(input, server, worldPosition, player, loot);
                    } finally {
                        // Keep any already-produced rewards even if a third-party hook fails. Never retry a failed roll automatically.
                        for (var stack : loot.items) enqueue(stack);
                        pendingXpFluid = Math.addExact(pendingXpFluid, Math.multiplyExact(loot.xp, 20));
                    }
                }
                if (gateway) {
                    lane.wave++;
                    if (GatewaySimulation.finished(lane.pearl, lane.wave)) lanes[i] = new Lane();
                    else lane.total = lane.remaining = 0;
                } else lane.total = lane.remaining = 0;
                cursor = (i+1)%capacity(); setChanged();
                // The paid step can export its completed rewards even when it used the final FE.
                flush();
                if (blocked()) break;
            } catch (RuntimeException error) {
                lane.error = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                lane.error = lane.error.substring(0, Math.min(180, lane.error.length())); setChanged();
            }
        }
    }
    private boolean pay(Lane lane, double amount) {
        // Prefer ME power, then cover any unpaid fraction from FE without double charging.
        if (node.isActive() && node.getGrid() != null) {
            double taken = node.getGrid().getEnergyService().extractAEPower(Math.max(0, amount-lane.credit), Actionable.MODULATE, PowerMultiplier.CONFIG);
            lane.credit += taken; if (taken > 0) setChanged();
        }
        if (lane.credit+0.00001 >= amount) { lane.credit = Math.max(0, lane.credit-amount); return true; }
        int fullCost = amount == 64 ? SimulatorConfig.GATEWAY_COST.get() : SimulatorConfig.SPAWNER_COST.get();
        int cost = (int)Math.ceil(fullCost * Math.max(0, 1-lane.credit/amount));
        if (fe.getEnergyStored() < cost) return false;
        storedFe = fe.getEnergyStored()-cost; lane.credit = 0; setChanged(); return true;
    }
    private void enqueue(ItemStack reward) {
        ItemStack stack = reward.copy();
        for (var existing : pending) if (ItemStack.isSameItemSameComponents(existing, stack)) {
            int move = Math.min(stack.getCount(), existing.getMaxStackSize()-existing.getCount());
            if (move > 0) { existing.grow(move); stack.shrink(move); }
        }
        while (!stack.isEmpty()) { int amount = Math.min(stack.getCount(), stack.getMaxStackSize()); pending.add(stack.copyWithCount(amount)); stack.shrink(amount); }
    }
    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i=0; i<MAX_SLOTS; i++) { drop(inventory.getStackInSlot(i)); inventory.setStackInSlot(i, ItemStack.EMPTY); lanes[i] = new Lane(); }
        for (int i=0; i<4; i++) { drop(upgrades.getStackInSlot(i)); upgrades.setStackInSlot(i, ItemStack.EMPTY); }
        drop(sword.getStackInSlot(0)); sword.setStackInSlot(0, ItemStack.EMPTY);
        pending.forEach(this::drop); pending.clear();
        if (pendingXpFluid > 0) drop(XpReceipt.create(pendingXpFluid));
        pendingXpFluid = 0; setChanged();
    }
    private void drop(ItemStack stack) { if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX()+.5, worldPosition.getY()+.5, worldPosition.getZ()+.5, stack.copy()); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (node.isReady()) node.saveToNBT(nodeData); tag.put("grid", nodeData.copy());
        if (owner != null) tag.putUUID("owner", owner);
        tag.putUUID("simulationId", simulationId); tag.put("ownerContext", ownerContext.copy());
        tag.put("sword", sword.serializeNBT(registries));
        tag.putBoolean("paused", paused); tag.putInt("cursor", cursor); tag.put("inventory", inventory.serializeNBT(registries));
        tag.putBoolean("adjacentOutput", adjacentOutput); tag.putInt("outputSides", outputSides);
        tag.putInt("storedFe", fe.getEnergyStored());
        tag.put("upgrades", upgrades.serializeNBT(registries));
        tag.putLong("pendingXpFluid", pendingXpFluid);
        ListTag outputs = new ListTag(); pending.forEach(s -> outputs.add(s.save(registries))); tag.put("pending", outputs);
        ListTag jobs = new ListTag();
        for (Lane lane : lanes) {
            CompoundTag job = new CompoundTag(); if (!lane.pearl.isEmpty()) job.put("pearl", lane.pearl.save(registries));
            job.putInt("wave", lane.wave); job.putInt("remaining", lane.remaining); job.putInt("total", lane.total);
            job.putDouble("credit", lane.credit); job.putString("error", lane.error); jobs.add(job);
        }
        tag.put("lanes", jobs);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        nodeData = tag.getCompound("grid").copy(); node.loadFromNBT(nodeData); owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        if (tag.hasUUID("simulationId")) simulationId = tag.getUUID("simulationId");
        ownerContext = tag.getCompound("ownerContext").copy();
        sword.deserializeNBT(registries, tag.getCompound("sword"));
        paused = tag.getBoolean("paused"); cursor = Math.floorMod(tag.getInt("cursor"), capacity());
        adjacentOutput = tag.getBoolean("adjacentOutput");
        storedFe = Math.clamp(tag.getInt("storedFe"), 0, SimulatorConfig.CAPACITY.get());
        outputSides = tag.contains("outputSides") ? tag.getInt("outputSides") & 63 : 1 << appeng.api.orientation.RelativeSide.BACK.ordinal();
        loading = true; try { inventory.deserializeNBT(registries, tag.getCompound("inventory")); } finally { loading = false; }
        upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
        pending.clear(); for (var raw : tag.getList("pending", 10)) { var stack = ItemStack.parseOptional(registries, (CompoundTag)raw); if (!stack.isEmpty()) pending.add(stack); }
        pendingXpFluid = Math.max(0, tag.getLong("pendingXpFluid"));
        Arrays.setAll(lanes, i -> new Lane()); var jobs = tag.getList("lanes", 10);
        for (int i=0; i<Math.min(MAX_SLOTS, jobs.size()); i++) {
            var job = jobs.getCompound(i); var lane = lanes[i]; lane.pearl = ItemStack.parseOptional(registries, job.getCompound("pearl"));
            lane.wave = Math.max(0, job.getInt("wave")); lane.remaining = job.getInt("remaining"); lane.total = Math.max(0, job.getInt("total"));
            lane.credit = Math.max(0, job.getDouble("credit")); lane.error = job.getString("error");
        }
    }
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new SimulatorMenu(id, inv, this, capacity()); }
}
