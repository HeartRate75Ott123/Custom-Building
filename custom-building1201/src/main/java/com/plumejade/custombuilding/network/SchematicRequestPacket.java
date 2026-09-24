package com.plumejade.custombuilding.network;

import java.util.function.Supplier;

import com.plumejade.custombuilding.blueprint.BlueprintSchematics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Asks the server for the blocks of a blueprint so the client can render the ghost preview. */
public class SchematicRequestPacket {
    private final ResourceLocation blueprintId;

    public SchematicRequestPacket(ResourceLocation blueprintId) {
        this.blueprintId = blueprintId;
    }

    public SchematicRequestPacket(FriendlyByteBuf buffer) {
        this.blueprintId = buffer.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(this.blueprintId);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        CBNetwork.sendToPlayer(player, new SchematicResponsePacket(BlueprintSchematics.load(level, this.blueprintId)));
    }
}
