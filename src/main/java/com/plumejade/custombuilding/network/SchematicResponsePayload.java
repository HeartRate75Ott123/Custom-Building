package com.plumejade.custombuilding.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.blueprint.BlueprintSchematic;
import com.plumejade.custombuilding.client.ClientSchematicCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sends the blocks of a structure to a client so it can draw the ghost preview.
 *
 * <p>Encoded compactly: a palette of block states plus var-int relative positions and palette indices.</p>
 */
public record SchematicResponsePayload(BlueprintSchematic schematic) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SchematicResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CustomBuilding.MODID, "schematic_response"));

    public static final StreamCodec<FriendlyByteBuf, SchematicResponsePayload> STREAM_CODEC =
            StreamCodec.of(SchematicResponsePayload::write, SchematicResponsePayload::read);

    private static void write(FriendlyByteBuf buffer, SchematicResponsePayload payload) {
        BlueprintSchematic schematic = payload.schematic;
        buffer.writeResourceLocation(schematic.blueprintId());

        Vec3i size = schematic.size();
        buffer.writeVarInt(size.getX());
        buffer.writeVarInt(size.getY());
        buffer.writeVarInt(size.getZ());

        // Deduplicate the block states; a building only uses a few dozen distinct states.
        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        List<Integer> indices = new ArrayList<>(schematic.states().size());
        for (BlockState state : schematic.states()) {
            indices.add(palette.computeIfAbsent(state, key -> palette.size()));
        }

        buffer.writeVarInt(palette.size());
        for (BlockState state : palette.keySet()) {
            buffer.writeVarInt(Block.getId(state));
        }

        buffer.writeVarInt(schematic.positions().size());
        for (int i = 0; i < schematic.positions().size(); i++) {
            BlockPos pos = schematic.positions().get(i);
            buffer.writeVarInt(pos.getX());
            buffer.writeVarInt(pos.getY());
            buffer.writeVarInt(pos.getZ());
            buffer.writeVarInt(indices.get(i));
        }
    }

    private static SchematicResponsePayload read(FriendlyByteBuf buffer) {
        ResourceLocation blueprintId = buffer.readResourceLocation();
        Vec3i size = new Vec3i(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());

        int paletteSize = buffer.readVarInt();
        List<BlockState> palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            palette.add(Block.stateById(buffer.readVarInt()));
        }

        int count = buffer.readVarInt();
        List<BlockPos> positions = new ArrayList<>(count);
        List<BlockState> states = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(new BlockPos(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
            int index = buffer.readVarInt();
            states.add(index >= 0 && index < palette.size() ? palette.get(index) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }

        return new SchematicResponsePayload(new BlueprintSchematic(blueprintId, size, positions, states));
    }

    @Override
    public CustomPacketPayload.Type<SchematicResponsePayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientSchematicCache.accept(this.schematic));
    }
}
