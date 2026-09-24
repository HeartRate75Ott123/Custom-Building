package com.plumejade.custombuilding.network;

import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.blueprint.BlueprintPlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent when the player presses "建造！" in the blueprint panel.
 *
 * @param blueprintId the blueprint being built
 * @param pos         the block that was right-clicked to open the panel
 * @param face        the face of that block that was clicked
 * @param facing      the direction the player chose in the panel
 * @param hand        the hand holding the blueprint
 */
public record BlueprintBuildPayload(ResourceLocation blueprintId,
                                    BlockPos pos,
                                    Direction face,
                                    Direction facing,
                                    InteractionHand hand) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BlueprintBuildPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CustomBuilding.MODID, "blueprint_build"));

    public static final StreamCodec<FriendlyByteBuf, BlueprintBuildPayload> STREAM_CODEC =
            StreamCodec.of(BlueprintBuildPayload::write, BlueprintBuildPayload::read);

    private static void write(FriendlyByteBuf buffer, BlueprintBuildPayload payload) {
        buffer.writeResourceLocation(payload.blueprintId);
        buffer.writeBlockPos(payload.pos);
        buffer.writeEnum(payload.face);
        buffer.writeEnum(payload.facing);
        buffer.writeEnum(payload.hand);
    }

    private static BlueprintBuildPayload read(FriendlyByteBuf buffer) {
        return new BlueprintBuildPayload(
                buffer.readResourceLocation(),
                buffer.readBlockPos(),
                buffer.readEnum(Direction.class),
                buffer.readEnum(Direction.class),
                buffer.readEnum(InteractionHand.class));
    }

    @Override
    public CustomPacketPayload.Type<BlueprintBuildPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlueprintPlacer.build(player, this);
            }
        });
    }
}
