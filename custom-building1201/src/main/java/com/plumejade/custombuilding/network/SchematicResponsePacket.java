package com.plumejade.custombuilding.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.plumejade.custombuilding.blueprint.BlueprintSchematic;
import com.plumejade.custombuilding.client.ClientSchematicCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

/**
 * Sends the blocks of a structure to a client so it can draw the ghost preview.
 *
 * <p>Encoded compactly: a palette of block states plus var-int relative positions and palette indices.</p>
 */
public class SchematicResponsePacket {
    private final BlueprintSchematic schematic;

    public SchematicResponsePacket(BlueprintSchematic schematic) {
        this.schematic = schematic;
    }

    public SchematicResponsePacket(FriendlyByteBuf buffer) {
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
            states.add(index >= 0 && index < palette.size() ? palette.get(index) : Blocks.AIR.defaultBlockState());
        }

        this.schematic = new BlueprintSchematic(blueprintId, size, positions, states);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(this.schematic.blueprintId());

        Vec3i size = this.schematic.size();
        buffer.writeVarInt(size.getX());
        buffer.writeVarInt(size.getY());
        buffer.writeVarInt(size.getZ());

        // Deduplicate the block states; a building only uses a few dozen distinct states.
        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        List<Integer> indices = new ArrayList<>(this.schematic.states().size());
        for (BlockState state : this.schematic.states()) {
            indices.add(palette.computeIfAbsent(state, key -> palette.size()));
        }

        buffer.writeVarInt(palette.size());
        for (BlockState state : palette.keySet()) {
            buffer.writeVarInt(Block.getId(state));
        }

        buffer.writeVarInt(this.schematic.positions().size());
        for (int i = 0; i < this.schematic.positions().size(); i++) {
            BlockPos pos = this.schematic.positions().get(i);
            buffer.writeVarInt(pos.getX());
            buffer.writeVarInt(pos.getY());
            buffer.writeVarInt(pos.getZ());
            buffer.writeVarInt(indices.get(i));
        }
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ClientSchematicCache.accept(this.schematic);
    }
}
