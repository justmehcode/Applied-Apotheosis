package dev.appliedapotheosis.client;

import dev.appliedapotheosis.AppliedApotheosis;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = AppliedApotheosis.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    public static final int ENTRO_COLOR = 0x039B8F;
    @SubscribeEvent public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AppliedApotheosis.SIMULATOR_ENTITY.get(), SimulatorRenderer::new);
    }
    @SubscribeEvent public static void blockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> tint == 1 ? ENTRO_COLOR : -1, AppliedApotheosis.EXTENDED.get(), AppliedApotheosis.EXTENDED_SIMULATOR.get());
    }
    @SubscribeEvent public static void itemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 1 ? ENTRO_COLOR : -1, AppliedApotheosis.EXTENDED_ITEM.get(), AppliedApotheosis.EXTENDED_SIMULATOR_ITEM.get());
    }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(AppliedApotheosis.CONTROLLER_MENU.get(), ControllerScreen::new);
        event.register(AppliedApotheosis.SIMULATOR_MENU.get(), SimulatorScreen::new);
    }
}
