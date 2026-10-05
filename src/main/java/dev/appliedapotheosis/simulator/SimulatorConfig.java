package dev.appliedapotheosis.simulator;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative FE settings; active costs apply per lane and processing step. */
public final class SimulatorConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue CAPACITY, RECEIVE, SPAWNER_COST, GATEWAY_COST;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> XP_FLUIDS;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("simulator");
        CAPACITY = builder.comment("FE buffer capacity for both simulator variants.").defineInRange("feCapacity", 100000, 1, Integer.MAX_VALUE);
        RECEIVE = builder.comment("Maximum FE accepted per receiveEnergy call; cables may call more than once per tick.").defineInRange("feMaxReceive", 10000, 1, Integer.MAX_VALUE);
        SPAWNER_COST = builder.comment("FE per active spawner lane per processing tick. Each acceleration step pays this cost.").defineInRange("spawnerFePerTick", 64, 1, Integer.MAX_VALUE);
        GATEWAY_COST = builder.comment("FE per active gateway lane per processing tick. Each acceleration step pays this cost.").defineInRange("gatewayFePerTick", 128, 1, Integer.MAX_VALUE);
        builder.pop();
        builder.push("infusion");
        XP_FLUIDS = builder.comment("Additional ME XP fluids: registry_id=mB per XP point. Missing fluids are ignored. Just Dire Things remains 20 mB/point.")
                .defineListAllowEmpty("xpFluids", java.util.List.of("industrialforegoing:essence=20"),
                        () -> "industrialforegoing:essence=20", value -> value instanceof String s && dev.appliedapotheosis.XpSources.validRate(s));
        builder.pop(); SPEC = builder.build();
    }
    private SimulatorConfig() {}
}
