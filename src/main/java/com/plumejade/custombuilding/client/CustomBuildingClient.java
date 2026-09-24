package com.plumejade.custombuilding.client;

import com.mojang.datafixers.util.Either;
import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.blueprint.BlueprintDefinition;
import com.plumejade.custombuilding.blueprint.BlueprintHelper;
import com.plumejade.custombuilding.network.ClientBlueprintSync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Client-only wiring: the dynamic item texture renderer, the preview image tooltip, the ghost preview and
 * the deferred creative tab rebuild.  The event bus is picked automatically from the event type.
 */
@EventBusSubscriber(modid = CustomBuilding.MODID, value = Dist.CLIENT)
public final class CustomBuildingClient {
    private CustomBuildingClient() {}

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return BlueprintItemRenderer.INSTANCE;
            }
        }, CustomBuilding.BLUEPRINT.get());
    }

    @SubscribeEvent
    public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(BlueprintPreviewTooltip.class, ClientBlueprintPreviewTooltip::new);
    }

    @SubscribeEvent
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        BlueprintDefinition definition = BlueprintHelper.definitionOf(event.getItemStack());
        if (definition == null || definition.preview() == null) {
            return;
        }
        Either<FormattedText, TooltipComponent> element =
                Either.right(new BlueprintPreviewTooltip(definition.preview()));
        event.getTooltipElements().add(element);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientBlueprintSync.tick();
    }

    /** Draws the ghost building. */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        BlueprintPreviewState.render(event);
    }

    /**
     * While the ghost is up the mouse buttons drive it directly, exactly like Litematica's overlay:
     * left click cancels, right click builds where the ghost stands.  Vanilla stops the use loop as soon as
     * the event is cancelled, so the off hand never gets a chance to act.
     */
    @SubscribeEvent
    public static void onInteractionKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack()) {
            if (!BlueprintPreviewState.isActive()) {
                return;
            }
            BlueprintPreviewState.cancel();
            event.setCanceled(true);
            return;
        }

        if (!event.isUseItem() || !BlueprintPreviewState.isActive()) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            BlueprintPreviewState.confirmBuild();
        }
    }

    /**
     * Sneak + mouse wheel turns the ghost around while it is shown.
     */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!BlueprintPreviewState.isActive()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isShiftKeyDown()) {
            return;
        }
        double delta = event.getScrollDeltaY();
        if (delta == 0.0D) {
            return;
        }
        BlueprintPreviewState.rotate(delta > 0.0D ? 1 : -1);
        // Swallow the scroll so the hotbar selection does not change as well.
        event.setCanceled(true);
    }

    /** Never leave a stale ghost behind when the player leaves the world. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        BlueprintPreviewState.clear();
        ClientSchematicCache.clear();
    }
}
