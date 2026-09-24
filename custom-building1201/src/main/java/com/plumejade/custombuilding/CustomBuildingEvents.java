package com.plumejade.custombuilding;

import com.plumejade.custombuilding.blueprint.BlueprintDefinitions;
import com.plumejade.custombuilding.blueprint.BlueprintReloadListener;
import com.plumejade.custombuilding.network.BlueprintSyncPacket;
import com.plumejade.custombuilding.network.CBNetwork;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server side and common game events. */
@Mod.EventBusSubscriber(modid = CustomBuilding.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
        BlueprintSyncPacket packet = new BlueprintSyncPacket(BlueprintDefinitions.serverSide());
        ServerPlayer player = event.getPlayer();
        if (player != null) {
            CBNetwork.sendToPlayer(player, packet);
            return;
        }
        for (ServerPlayer other : event.getPlayerList().getPlayers()) {
            CBNetwork.sendToPlayer(other, packet);
        }
    }
}
