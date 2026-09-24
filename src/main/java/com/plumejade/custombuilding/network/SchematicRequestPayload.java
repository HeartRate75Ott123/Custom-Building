package com.plumejade.custombuilding.network;

import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.blueprint.BlueprintSchematics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Asks the server for the blocks of a blueprint so the client can render the ghost preview. */
public record SchematicRequestPayload(ResourceLocation blueprintId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SchematicRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CustomBuilding.MODID, "schematic_request"));

    public static final StreamCodec<FriendlyByteBuf, SchematicRequestPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> buffer.writeResourceLocation(payload.blueprintId),
                    buffer -> new SchematicRequestPayload(buffer.readResourceLocation()));

    @Override
    public CustomPacketPayload.Type<SchematicRequestPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player,
                        new SchematicResponsePayload(BlueprintSchematics.load(player.serverLevel(), this.blueprintId)));
            }
        });
    }
}
