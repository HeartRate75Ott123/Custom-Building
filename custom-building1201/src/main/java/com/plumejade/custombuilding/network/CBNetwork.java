package com.plumejade.custombuilding.network;

import com.plumejade.custombuilding.CustomBuilding;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Networking, built on Forge's {@link SimpleChannel}. */
public final class CBNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CustomBuilding.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private CBNetwork() {}

    public static void register() {
        int id = 0;

        CHANNEL.messageBuilder(BlueprintSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BlueprintSyncPacket::encode)
                .decoder(BlueprintSyncPacket::new)
                .consumerMainThread(BlueprintSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(SchematicResponsePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SchematicResponsePacket::encode)
                .decoder(SchematicResponsePacket::new)
                .consumerMainThread(SchematicResponsePacket::handle)
                .add();

        CHANNEL.messageBuilder(BlueprintBuildPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BlueprintBuildPacket::encode)
                .decoder(BlueprintBuildPacket::new)
                .consumerMainThread(BlueprintBuildPacket::handle)
                .add();

        CHANNEL.messageBuilder(SchematicRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SchematicRequestPacket::encode)
                .decoder(SchematicRequestPacket::new)
                .consumerMainThread(SchematicRequestPacket::handle)
                .add();
    }

    /** Sends the complete blueprint list to one player. */
    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** Sends a message from the client to the server. */
    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
