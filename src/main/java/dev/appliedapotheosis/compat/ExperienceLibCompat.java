package dev.appliedapotheosis.compat;

import es.degrassi.experiencelib.api.capability.ExperienceLibCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Loaded only when Experience Lib is installed. Native XP is measured in whole points. */
public final class ExperienceLibCompat {
    private ExperienceLibCompat() {}

    public static long extractAdjacent(Level level, BlockPos origin, long units, boolean simulate) {
        long remaining=units/20;
        long requested=remaining;
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<es.degrassi.experiencelib.api.capability.IExperienceHandler,Boolean>());
        for (Direction side:Direction.values()) {
            var pos=origin.relative(side);
            if(!level.hasChunkAt(pos)) continue;
            var handler=level.getCapability(ExperienceLibCapabilities.EXPERIENCE.block(),pos,side.getOpposite());
            if(handler==null || !seen.add(handler)) continue;
            for(int tank=0;tank<handler.getTanks() && remaining>0;tank++)
                remaining-=Math.clamp(handler.extractExperience(tank,remaining,simulate),0,remaining);
            if(remaining==0) break;
        }
        return (requested-remaining)*20;
    }

    public static long export(Level level, BlockPos target, Direction face, long fluidUnits) {
        long points = fluidUnits / 20;
        if (points <= 0) return 0;
        var handler = level.getCapability(ExperienceLibCapabilities.EXPERIENCE.block(), target, face);
        if (handler == null) return 0;
        long remaining = points;
        for (int tank = 0; tank < handler.getTanks() && remaining > 0; tank++) {
            long received = Math.clamp(handler.receiveExperience(tank, remaining, false), 0, remaining);
            remaining -= received;
        }
        return (points - remaining) * 20;
    }
}
