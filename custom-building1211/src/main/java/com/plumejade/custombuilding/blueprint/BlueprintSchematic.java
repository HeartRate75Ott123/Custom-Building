package com.plumejade.custombuilding.blueprint;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A structure template flattened into something the client can render.
 *
 * <p>Structure templates keep their blocks behind a private field, so this is read straight from the
 * {@code .nbt} file.  Air is dropped and positions stay relative to the template origin.</p>
 */
public record BlueprintSchematic(ResourceLocation blueprintId,
                                 Vec3i size,
                                 List<BlockPos> positions,
                                 List<BlockState> states) {

    public static final BlueprintSchematic EMPTY =
            new BlueprintSchematic(ResourceLocation.fromNamespaceAndPath("minecraft", "empty"), Vec3i.ZERO, List.of(), List.of());

    public boolean isEmpty() {
        return this.positions.isEmpty();
    }

    /** Builds a schematic from the contents of a structure template file. */
    public static BlueprintSchematic read(ResourceLocation blueprintId, CompoundTag tag, HolderGetter<Block> blockLookup) {
        ListTag sizeTag = tag.getList("size", Tag.TAG_INT);
        Vec3i size = sizeTag.size() == 3
                ? new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2))
                : Vec3i.ZERO;

        List<BlockState> palette = readPalette(tag, blockLookup);

        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag entry = blocksTag.getCompound(i);
            ListTag posTag = entry.getList("pos", Tag.TAG_INT);
            if (posTag.size() != 3) {
                continue;
            }
            int index = entry.getInt("state");
            if (index < 0 || index >= palette.size()) {
                continue;
            }
            BlockState state = palette.get(index);
            if (state.isAir()) {
                continue;
            }
            positions.add(new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2)));
            states.add(state);
        }

        return new BlueprintSchematic(blueprintId, size, List.copyOf(positions), List.copyOf(states));
    }

    private static List<BlockState> readPalette(CompoundTag tag, HolderGetter<Block> blockLookup) {
        ListTag paletteTag;
        if (tag.contains("palette", Tag.TAG_LIST)) {
            paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        } else {
            ListTag palettes = tag.getList("palettes", Tag.TAG_LIST);
            paletteTag = palettes.isEmpty() ? new ListTag() : palettes.getList(0);
        }

        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompound(i)));
        }
        return palette;
    }
}
