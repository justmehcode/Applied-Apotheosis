package dev.appliedapotheosis;

import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.*;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.core.definitions.AEItems;
import dev.shadowsoffire.apothic_enchanting.Ench;
import dev.shadowsoffire.apothic_enchanting.table.infusion.InfusionRecipe;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class ControllerBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost, ICraftingMachine, MenuProvider {
    public static final ResourceLocation XP_FLUID = ResourceLocation.fromNamespaceAndPath("justdirethings", "xp_fluid_source");
    // Fifth lane exists only to preserve work saved by the 0.1 prototype, which allowed five jobs.
    static final int SAVED_LANES = 5;
    private IManagedGridNode node;
    private CompoundTag nodeData = new CompoundTag();
    private UUID owner;
    private Boolean personalXp;
    private final UUID[] operators = new UUID[SAVED_LANES];
    private final IActionSource source = IActionSource.ofMachine(this);
    final InfusionJob[] jobs = new InfusionJob[SAVED_LANES];
    final ItemStackHandler inventory = new ItemStackHandler(3 * SAVED_LANES) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot % 3 == 0 || slot % 3 == 1 && stack.is(Items.LAPIS_LAZULI);
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    final ItemStackHandler upgrades = new ItemStackHandler(4) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(AEItems.SPEED_CARD.asItem()); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(AppliedApotheosis.CONTROLLER_ENTITY.get(), pos, state);
        node = createNode();
    }
    private IManagedGridNode createNode() {
        var result = GridHelper.createManagedNode(this, (ControllerBlockEntity be, IGridNode n) -> be.setChanged())
                .setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(4).setInWorldNode(true)
                .setVisualRepresentation(getBlockState().getBlock());
        result.loadFromNBT(nodeData);
        return result;
    }
    public IManagedGridNode node() { return node; }
    public void setOwner(Player player) { owner = player.getUUID(); node.setOwningPlayer(player); setChanged(); }
    @Override public IGridNode getGridNode(Direction side) { return node.getNode(); }
    @Override public IGridNode getActionableNode() { return node.getNode(); }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) GridHelper.onFirstTick(this, be -> {
            if (be.isRemoved() || be.node.isReady()) return;
            if (be.owner != null && be.level.getPlayerByUUID(be.owner) != null)
                be.node.setOwningPlayer(be.level.getPlayerByUUID(be.owner));
            be.node.create(be.level, be.worldPosition);
        });
    }
    private void suspendNode() {
        if (node.isReady()) node.saveToNBT(nodeData);
        node.destroy(); node = createNode();
    }
    @Override public void setRemoved() { suspendNode(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { suspendNode(); super.onChunkUnloaded(); }
    public int capacity() { return getBlockState().is(AppliedApotheosis.EXTENDED.get()) ? 4 : 1; }
    public int pages() {
        int n = capacity();
        for (int i = n; i < SAVED_LANES; i++) if (jobs[i] != null || !inventory.getStackInSlot(3*i).isEmpty()
                || !inventory.getStackInSlot(3*i+1).isEmpty() || !inventory.getStackInSlot(3*i+2).isEmpty()) n = i+1;
        return n;
    }
    public int speed() {
        int n = 1;
        for (int i = 0; i < 4; i++) if (upgrades.getStackInSlot(i).is(AEItems.SPEED_CARD.asItem())) n++;
        return n;
    }
    public int jobCount() { return (int) Arrays.stream(jobs).filter(Objects::nonNull).count(); }
    public boolean usesPlayerXp() { return personalXp == null ? !connected() : personalXp; }
    public void toggleXpSource() {
        for (int lane=0; lane<SAVED_LANES; lane++) if (jobs[lane] != null && !jobs[lane].automated) cancel(lane);
        personalXp = !usesPlayerXp(); setChanged();
    }
    public boolean canEditInputs(int lane) { return jobs[lane] == null || jobs[lane].holdsInputs; }
    public void editInputs(int lane, Player player) {
        cancel(lane); operators[lane] = player.getUUID(); setChanged();
    }
    private void refund(InfusionJob job) {
        if (job.paid <= 0) return;
        if (job.personalPayer != null) {
            // A payer may have changed dimension since starting the infusion.
            Player player = level.getServer().getPlayerList().getPlayer(job.personalPayer);
            if (player == null) player = level.getPlayerByUUID(job.personalPayer);
            if (player != null) player.giveExperiencePoints((int) (job.paid/20));
            else net.minecraft.world.entity.ExperienceOrb.award((net.minecraft.server.level.ServerLevel) level,
                    worldPosition.getCenter(), (int) (job.paid/20));
        } else {
            var grid = node.getGrid(); var key = xpKey();
            long returned=0;
            if(net.neoforged.fml.ModList.get().isLoaded("experiencelib"))
                for(Direction side:Direction.values()) {
                    var pos=worldPosition.relative(side);
                    if(level.hasChunkAt(pos)) returned+=dev.appliedapotheosis.compat.ExperienceLibCompat.export(level,pos,side.getOpposite(),job.paid-returned);
                }
            if(node.isActive() && grid != null && key != null)
                returned += grid.getStorageService().getInventory().insert(key, job.paid-returned, Actionable.MODULATE, source);
            if (returned < job.paid) drop(XpReceipt.create(job.paid-returned));
        }
        job.paid = 0;
    }
    private void cancel(int lane) {
        var job = jobs[lane];
        if (job == null || !job.holdsInputs) return;
        jobs[lane] = null; refund(job); setChanged();
    }
    public boolean connected() { return node.getNode() != null && !node.getNode().getConnections().isEmpty(); }
    public AEFluidKey xpKey() {
        return BuiltInRegistries.FLUID.containsKey(XP_FLUID) ? AEFluidKey.of(BuiltInRegistries.FLUID.get(XP_FLUID)) : null;
    }
    public long availableXpFluid() {
        return takeXp(Long.MAX_VALUE, Actionable.SIMULATE);
    }
    private long takeXp(long limit, Actionable mode) {
        var grid=node.getGrid(); var key=xpKey();
        var storage=grid!=null && node.isActive() ? grid.getStorageService().getInventory() : null;
        long taken=storage==null || key==null ? 0 : storage.extract(key,limit,mode,source);
        taken+=XpSources.transfer(storage,source,limit-taken,mode,this::drop);
        if(level!=null && !level.isClientSide && net.neoforged.fml.ModList.get().isLoaded("experiencelib"))
            taken+=dev.appliedapotheosis.compat.ExperienceLibCompat.extractAdjacent(level,worldPosition,limit-taken,mode==Actionable.SIMULATE);
        return taken;
    }
    /** Small, server-authored inspection snapshot; never sends item contents or player UUIDs. */
    public CompoundTag inspectionData() {
        var data = new CompoundTag();
        data.putInt("capacity", capacity()); data.putInt("active", jobCount()); data.putInt("speed", speed());
        data.putBoolean("personal", usesPlayerXp()); data.putBoolean("online", node.isActive());
        data.putBoolean("connected", connected());
        var list = new ListTag();
        for (int lane=0; lane<SAVED_LANES; lane++) {
            var job = jobs[lane]; if (job == null) continue;
            var entry = new CompoundTag(); entry.putInt("lane", lane+1);
            entry.putInt("remaining", job.remaining); entry.putBoolean("personal", job.personalPayer != null);
            entry.putString("item", BuiltInRegistries.ITEM.getKey(job.output.getItem()).toString());
            entry.putInt("count", job.output.getCount());
            String name = job.output.getHoverName().getString();
            entry.putString("name", name.substring(0, Math.min(name.length(), 100)));
            list.add(entry);
        }
        data.put("jobs", list); return data;
    }
    public List<RecipeHolder<InfusionRecipe>> recipes(int lane) {
        if (level == null) return List.of();
        ItemStack input = inventory.getStackInSlot(lane * 3);
        return level.getRecipeManager().getAllRecipesFor(Ench.RecipeTypes.INFUSION).stream()
                .filter(h -> InfusionRules.supported(h.value()) && h.value().getInput().test(input))
                .sorted(Comparator.comparing(h -> h.id().toString())).toList();
    }
    private InfusionJob plan(RecipeHolder<InfusionRecipe> holder, ItemStack input, boolean automated) {
        var recipe = holder.value(); var stats = recipe.getRequirements();
        int e = InfusionRules.selectedStat(stats.eterna()), q = InfusionRules.selectedStat(stats.quanta()), a = InfusionRules.selectedStat(stats.arcana());
        if (!recipe.matches(input, e, q, a)) return null;
        var output = recipe.assemble(input.copyWithCount(1), e, q, a);
        if (output.isEmpty()) return null;
        var job = new InfusionJob(holder.id(), input, output, InfusionRules.fluidCost(recipe), automated, e, q, a);
        job.holdsInputs = true; return job;
    }
    public boolean startManual(int lane, int recipeIndex, Player player) {
        if (level == null || level.isClientSide || lane < 0 || lane >= capacity() || jobs[lane] != null) return false;
        var matches = recipes(lane);
        if (recipeIndex < 0 || recipeIndex >= matches.size() || !inventory.getStackInSlot(3*lane+1).is(Items.LAPIS_LAZULI)
                || inventory.getStackInSlot(3*lane+1).getCount() < 3) return false;
        InfusionJob job = plan(matches.get(recipeIndex), inventory.getStackInSlot(3*lane), false);
        if (job == null || !fitsOutput(lane, job.output)) return false;
        if (usesPlayerXp()) {
            if (player == null) return false;
            long points = ExperienceMath.totalAtLevel(player.experienceLevel)
                    + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
            if (!player.getAbilities().instabuild && points < job.cost / 20) return false;
            if (!player.getAbilities().instabuild) player.giveExperiencePoints(-(int) (job.cost / 20));
            job.paid = player.getAbilities().instabuild ? 0 : job.cost;
            job.personalPayer = player.getUUID();
            job.remaining = InfusionRules.DURATION;
        }
        jobs[lane] = job;
        if (job.paid == job.cost) commitIngredients(lane, job);
        setChanged(); return true;
    }
    private void commitIngredients(int lane, InfusionJob job) {
        if (!job.holdsInputs) {
            inventory.extractItem(3*lane, 1, false); inventory.extractItem(3*lane+1, 3, false);
        }
        job.remaining = InfusionRules.DURATION;
    }
    private boolean fitsOutput(int lane, ItemStack output) {
        var existing = inventory.getStackInSlot(3*lane+2);
        return existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, output)
                && existing.getCount() + output.getCount() <= existing.getMaxStackSize();
    }
    @Override public PatternContainerGroup getCraftingMachineInfo() {
        return new PatternContainerGroup(AEItemKey.of(getBlockState().getBlock()), getDisplayName(),
                List.of(Component.literal(capacity() + " job slots, " + speed() + "x speed")));
    }
    @Override public boolean acceptsPlans() { return true; }
    @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] counters, Direction side) {
        if (level == null || level.isClientSide || !node.isActive() || pattern.getOutputs().size() != 1
                || !pattern.supportsPushInputsToExternalInventory()) return false;
        List<Integer> free = new ArrayList<>();
        for (int i=0; i<capacity(); i++) if (jobs[i] == null && inventory.getStackInSlot(3*i).isEmpty()
                && inventory.getStackInSlot(3*i+1).isEmpty() && inventory.getStackInSlot(3*i+2).isEmpty()) free.add(i);
        if (free.isEmpty()) return false;
        var requested = pattern.getPrimaryOutput();
        if (!(requested.what() instanceof AEItemKey wanted) || requested.amount() <= 0) return false;
        KeyCounter delivered = new KeyCounter();
        for (var counter : counters) for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey) || entry.getLongValue() <= 0 || entry.getLongValue() > 320) return false;
            delivered.add(entry.getKey(), entry.getLongValue());
        }
        var lapis = AEItemKey.of(Items.LAPIS_LAZULI);
        List<InfusionJob> selected = null;
        for (var holder : level.getRecipeManager().getAllRecipesFor(Ench.RecipeTypes.INFUSION)) {
            if (!InfusionRules.supported(holder.value())) continue;
            for (var entry : delivered) {
                AEItemKey inputKey = (AEItemKey) entry.getKey();
                var job = plan(holder, inputKey.toStack(1), true);
                if (job == null || !wanted.equals(AEItemKey.of(job.output)) || requested.amount() % job.output.getCount() != 0) continue;
                long count = requested.amount() / job.output.getCount();
                if (count < 1 || count > free.size()) continue;
                KeyCounter required = new KeyCounter(); required.add(inputKey, count); required.add(lapis, 3*count);
                if (required.size() != delivered.size()) continue;
                boolean equal = true;
                for (var need : required) if (need.getLongValue() != delivered.get(need.getKey())) equal = false;
                if (!equal) continue;
                if (selected != null) return false;
                selected = new ArrayList<>();
                for (int i=0; i<count; i++) selected.add(plan(holder, inputKey.toStack(1), true));
            }
        }
        if (selected == null) return false;
        for (int i=0; i<selected.size(); i++) {
            int lane = free.get(i); InfusionJob job = selected.get(i); jobs[lane] = job;
            inventory.setStackInSlot(3*lane, job.preview.copy());
            inventory.setStackInSlot(3*lane+1, new ItemStack(Items.LAPIS_LAZULI, 3));
        }
        for (var counter : counters) counter.clear();
        setChanged(); return true;
    }
    public void serverTick() {
        if (level == null || level.isClientSide) return;
        for (int lane=0; lane<capacity(); lane++) if (jobs[lane] == null)
            startManual(lane, 0, operators[lane] == null ? null : level.getPlayerByUUID(operators[lane]));
        var grid = node.getGrid(); var key = xpKey();
        var storage = node.isActive() && grid != null ? grid.getStorageService().getInventory() : null;
        boolean changed = false;
        for (int lane=0; lane<SAVED_LANES; lane++) {
            var job = jobs[lane]; if (job == null) continue;
            if (job.holdsInputs && (!ItemStack.isSameItemSameComponents(inventory.getStackInSlot(3*lane), job.preview)
                    || !inventory.getStackInSlot(3*lane+1).is(Items.LAPIS_LAZULI)
                    || inventory.getStackInSlot(3*lane+1).getCount() < 3)) { cancel(lane); changed = true; continue; }
            if (job.remaining < 0) {
                long due = job.cost - job.paid;
                if (takeXp(due, Actionable.SIMULATE) < due) continue;
                long taken = takeXp(due, Actionable.MODULATE);
                job.paid += taken; changed |= taken > 0;
                if (job.paid < job.cost) continue;
                commitIngredients(lane, job); changed = true;
            }
            if (job.automated && storage == null) continue;
            if (job.remaining > 0) { job.remaining = Math.max(0, job.remaining - speed()); changed = true; }
            if (job.remaining == 0) {
                if (job.holdsInputs) {
                    inventory.extractItem(3*lane, 1, false); inventory.extractItem(3*lane+1, 3, false);
                    job.holdsInputs = false; changed = true;
                }
                if (job.automated) {
                    if (storage == null) continue;
                    long inserted = storage.insert(AEItemKey.of(job.output), job.output.getCount(), Actionable.MODULATE, source);
                    if (inserted > 0) { job.output.shrink((int) inserted); changed = true; }
                    if (job.output.isEmpty()) jobs[lane] = null;
                } else if (fitsOutput(lane, job.output)) {
                    var result = inventory.getStackInSlot(3*lane+2);
                    inventory.setStackInSlot(3*lane+2, result.isEmpty() ? job.output.copy()
                            : result.copyWithCount(result.getCount()+job.output.getCount()));
                    jobs[lane] = null; changed = true;
                }
            }
        }
        if (changed) setChanged();
    }
    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i=0; i<upgrades.getSlots(); i++) { drop(upgrades.getStackInSlot(i)); upgrades.setStackInSlot(i, ItemStack.EMPTY); }
        for (int i=0; i<inventory.getSlots(); i++) { drop(inventory.getStackInSlot(i)); inventory.setStackInSlot(i, ItemStack.EMPTY); }
        for (int lane=0; lane<SAVED_LANES; lane++) {
            var job = jobs[lane];
            if (job != null && job.holdsInputs) refund(job);
            else if (job != null && job.remaining >= 0) drop(job.output);
            // Preserve fractional XP payments exactly in a redeemable receipt, not an internal fluid tank.
            if (job != null && !job.holdsInputs && job.remaining < 0 && job.paid > 0) drop(XpReceipt.create(job.paid));
            jobs[lane] = null;
        }
        setChanged();
    }
    private void drop(ItemStack stack) { if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX()+.5, worldPosition.getY()+.5, worldPosition.getZ()+.5, stack.copy()); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (node.isReady()) node.saveToNBT(nodeData); tag.put("grid", nodeData.copy());
        if (owner != null) tag.putUUID("owner", owner);
        if (personalXp != null) tag.putBoolean("personalXp", personalXp);
        for (int i=0; i<SAVED_LANES; i++) if (operators[i] != null) tag.putUUID("operator"+i, operators[i]);
        tag.putInt("schema", 2); tag.put("inventory", inventory.serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries)); ListTag list = new ListTag();
        for (int i=0; i<SAVED_LANES; i++) if (jobs[i] != null) { var data = jobs[i].save(registries); data.putInt("lane", i); list.add(data); }
        tag.put("jobs", list);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        nodeData = tag.contains("grid") ? tag.getCompound("grid").copy() : tag.copy();
        node.loadFromNBT(nodeData); owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        personalXp = tag.contains("personalXp") ? tag.getBoolean("personalXp") : null;
        for (int i=0; i<SAVED_LANES; i++) operators[i] = tag.hasUUID("operator"+i) ? tag.getUUID("operator"+i) : null;
        if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        upgrades.deserializeNBT(registries, tag.getCompound("upgrades")); Arrays.fill(jobs, null);
        int oldIndex = 0;
        for (var entry : tag.getList("jobs", 10)) {
            CompoundTag data = (CompoundTag) entry;
            int lane = data.contains("lane") ? data.getInt("lane") : oldIndex++;
            if (lane < 0 || lane >= SAVED_LANES) continue;
            jobs[lane] = InfusionJob.load(data, registries);
            if (!tag.contains("schema") && jobs[lane].remaining < 0) {
                inventory.setStackInSlot(3*lane, ItemStack.parseOptional(registries, data.getCompound("input")));
                inventory.setStackInSlot(3*lane+1, ItemStack.parseOptional(registries, data.getCompound("lapis")));
            }
        }
    }
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new ControllerMenu(id, inventory, this); }
}
