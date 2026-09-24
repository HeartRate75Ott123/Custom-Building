package com.plumejade.custombuilding;

import com.plumejade.custombuilding.blueprint.BlueprintDefinitions;
import com.plumejade.custombuilding.blueprint.BlueprintReloadListener;
import com.plumejade.custombuilding.network.BlueprintSyncPayload;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server side and common game events. */
@EventBusSubscriber(modid = CustomBuilding.MODID)
public final class CustomBuildingEvents {
    private CustomBuildingEvents() {}

    /** Hooks our blueprint parser into every datapack (re)load. */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new BlueprintReloadListener());
    }

    /** Sends the freshly loaded blueprint list to the clients on join and after every /reload. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        BlueprintSyncPayload payload = new BlueprintSyncPayload(BlueprintDefinitions.serverSide());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }
}
