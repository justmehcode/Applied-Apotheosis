package dev.appliedapotheosis;

import appeng.api.AECapabilities;
import dev.appliedapotheosis.simulator.*;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(AppliedApotheosis.ID)
public final class AppliedApotheosis {
    public static final String ID = "applied_apotheosis";
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final DeferredHolder<Block, ControllerBlock> CONTROLLER = BLOCKS.register("infusion_controller",
            () -> new ControllerBlock(BlockBehaviour.Properties.of().strength(4, 30).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item, BlockItem> CONTROLLER_ITEM = ITEMS.register("infusion_controller",
            () -> new BlockItem(CONTROLLER.get(), new Item.Properties()));
    public static final DeferredHolder<Block, ControllerBlock> EXTENDED = BLOCKS.register("extended_infusion_controller",
            () -> new ControllerBlock(BlockBehaviour.Properties.of().strength(4, 30).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item, BlockItem> EXTENDED_ITEM = ITEMS.register("extended_infusion_controller",
            () -> new BlockItem(EXTENDED.get(), new Item.Properties()));
    public static final DeferredHolder<Item, CapturedRewardItem> CAPTURED_REWARD = ITEMS.register("captured_gateway_reward", CapturedRewardItem::new);
    public static final DeferredHolder<Item, XpReceipt> XP_RECEIPT = ITEMS.register("xp_receipt", XpReceipt::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControllerBlockEntity>> CONTROLLER_ENTITY =
            ENTITIES.register("infusion_controller", () -> BlockEntityType.Builder.of(ControllerBlockEntity::new, CONTROLLER.get(), EXTENDED.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> CONTROLLER_MENU =
            MENUS.register("infusion_controller", () -> IMenuTypeExtension.create(ControllerMenu::fromNetwork));

    public static final DeferredHolder<Block, SimulatorBlock> SIMULATOR = BLOCKS.register("me_simulator",
            () -> new SimulatorBlock(BlockBehaviour.Properties.of().strength(4, 30).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Block, SimulatorBlock> EXTENDED_SIMULATOR = BLOCKS.register("extended_me_simulator",
            () -> new SimulatorBlock(BlockBehaviour.Properties.of().strength(4, 30).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item, BlockItem> SIMULATOR_ITEM = ITEMS.register("me_simulator", () -> new BlockItem(SIMULATOR.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> EXTENDED_SIMULATOR_ITEM = ITEMS.register("extended_me_simulator", () -> new BlockItem(EXTENDED_SIMULATOR.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SimulatorBlockEntity>> SIMULATOR_ENTITY =
            ENTITIES.register("me_simulator", () -> BlockEntityType.Builder.of(SimulatorBlockEntity::new, SIMULATOR.get(), EXTENDED_SIMULATOR.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<SimulatorMenu>> SIMULATOR_MENU =
            MENUS.register("me_simulator", () -> IMenuTypeExtension.create(SimulatorMenu::fromNetwork));

    public AppliedApotheosis(IEventBus bus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, SimulatorConfig.SPEC, "appliedapotheosis-server.toml");
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus);
        bus.addListener(this::capabilities);
        bus.addListener(this::setup);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                event.accept(CONTROLLER_ITEM.get()); event.accept(EXTENDED_ITEM.get());
                event.accept(SIMULATOR_ITEM.get()); event.accept(EXTENDED_SIMULATOR_ITEM.get());
            }
        });
    }
    private void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, SIMULATOR_ENTITY.get(), (be, ctx) -> be);
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, SIMULATOR_ENTITY.get(), (be, side) -> be.feStorage());
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, CONTROLLER_ENTITY.get(), (be, ctx) -> be);
        event.registerBlockEntity(AECapabilities.CRAFTING_MACHINE, CONTROLLER_ENTITY.get(), (be, side) -> be);
    }
    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Upgrades.add(AEItems.SPEED_CARD, SIMULATOR_ITEM.get(), 4);
            Upgrades.add(AEItems.SPEED_CARD, EXTENDED_SIMULATOR_ITEM.get(), 4);
            Upgrades.add(AEItems.SPEED_CARD, CONTROLLER_ITEM.get(), 4);
            Upgrades.add(AEItems.SPEED_CARD, EXTENDED_ITEM.get(), 4);
        });
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
}
