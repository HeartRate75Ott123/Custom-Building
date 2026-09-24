package com.plumejade.custombuilding.client;

import com.mojang.datafixers.util.Either;
import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.blueprint.BlueprintDefinition;
import com.plumejade.custombuilding.blueprint.BlueprintHelper;
import com.plumejade.custombuilding.network.ClientBlueprintSync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only wiring on the Forge event bus: the preview image tooltip, the ghost preview, the input
 * handling and the deferred creative tab rebuild.
 */
@Mod.EventBusSubscriber(modid = CustomBuilding.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CustomBuildingClient {
    private CustomBuildingClient() {}

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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientBlueprintSync.tick();
        }
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

    /** Sneak + mouse wheel turns the ghost around while it is shown. */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!BlueprintPreviewState.isActive()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isShiftKeyDown()) {
            return;
        }
        double delta = event.getScrollDelta();
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
