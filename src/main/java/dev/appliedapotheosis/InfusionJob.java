package dev.appliedapotheosis;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Persisted recipe and payment receipt; contains no pattern and no fluid tank. */
final class InfusionJob {
    final ResourceLocation recipe;
    final ItemStack preview;
    ItemStack output;
    final long cost;
    final boolean automated;
    final int eterna, quanta, arcana;
    long paid;
    int remaining = -1;
    boolean holdsInputs;
    java.util.UUID personalPayer;
    InfusionJob(ResourceLocation recipe, ItemStack preview, ItemStack output, long cost, boolean automated,
                int eterna, int quanta, int arcana) {
        this.recipe = recipe; this.preview = preview.copyWithCount(1); this.output = output.copy();
        this.cost = cost; this.automated = automated;
        this.eterna = eterna; this.quanta = quanta; this.arcana = arcana;
    }
    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", recipe.toString()); tag.put("preview", preview.saveOptional(registries));
        tag.put("output", output.saveOptional(registries)); tag.putBoolean("automated", automated);
        tag.putLong("cost", cost); tag.putLong("paid", paid); tag.putInt("remaining", remaining);
        tag.putInt("eterna", eterna); tag.putInt("quanta", quanta); tag.putInt("arcana", arcana);
        tag.putBoolean("holdsInputs", holdsInputs);
        if (personalPayer != null) tag.putUUID("personalPayer", personalPayer);
        return tag;
    }
    static InfusionJob load(CompoundTag tag, HolderLookup.Provider registries) {
        InfusionJob job = new InfusionJob(ResourceLocation.parse(tag.getString("recipe")),
                ItemStack.parseOptional(registries, tag.getCompound(tag.contains("preview") ? "preview" : "input")),
                ItemStack.parseOptional(registries, tag.getCompound("output")), tag.getLong("cost"),
                !tag.contains("automated") || tag.getBoolean("automated"), tag.getInt("eterna"), tag.getInt("quanta"), tag.getInt("arcana"));
        job.paid = tag.getLong("paid"); job.remaining = tag.getInt("remaining");
        job.holdsInputs = tag.getBoolean("holdsInputs");
        job.personalPayer = tag.hasUUID("personalPayer") ? tag.getUUID("personalPayer") : null;
        return job;
    }
}
