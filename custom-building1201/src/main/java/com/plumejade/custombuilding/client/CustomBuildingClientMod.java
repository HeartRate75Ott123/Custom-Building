package com.plumejade.custombuilding.client;

import com.plumejade.custombuilding.CustomBuilding;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only registrations that belong on the mod event bus. */
@Mod.EventBusSubscriber(modid = CustomBuilding.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CustomBuildingClientMod {
    private CustomBuildingClientMod() {}

    @SubscribeEvent
    public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(BlueprintPreviewTooltip.class, ClientBlueprintPreviewTooltip::new);
    }
}
