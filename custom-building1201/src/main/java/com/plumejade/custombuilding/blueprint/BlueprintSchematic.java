package com.plumejade.custombuilding.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.plumejade.custombuilding.CustomBuilding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * A structure template flattened into something that can be rendered and placed.
 *
 * <p>Structure templates keep their blocks behind a private field and, more importantly, vanilla parses the
 * palette with {@code HolderGetter#getOrThrow}: a single block id that does not exist in this Minecraft
 * version - a block from an optional mod, or a block that was renamed after this version - makes the whole
 * template fail to load.  This reader is tolerant instead: unknown blocks become air and are reported once,
 * so a blueprint still builds with whatever the current instance understands.</p>
 */
public record BlueprintSchematic(ResourceLocation blueprintId,
                                 Vec3i size,
                                 List<BlockPos> positions,
                                 List<BlockState> states,
                                 List<CompoundTag> blockEntities) {

    public static final BlueprintSchematic EMPTY =
            new BlueprintSchematic(new ResourceLocation("minecraft", "empty"), Vec3i.ZERO, List.of(), List.of(), List.of());

    /**
     * Block ids that were renamed after 1.20.1.  A structure saved by a newer version still builds instead of
     * silently losing those blocks.
     */
    private static final Map<ResourceLocation, ResourceLocation> RENAMES = Map.of(
            new ResourceLocation("minecraft:short_grass"), new ResourceLocation("minecraft:grass"));

    public boolean isEmpty() {
        return this.positions.isEmpty();
    }

    /** {@return the block entity data of a block, or {@code null} when there is none} */
    @Nullable
    public CompoundTag blockEntity(int index) {
        return this.blockEntities.size() == this.positions.size() ? this.blockEntities.get(index) : null;
    }

    /** Builds a schematic from the contents of a structure template file. */
    public static BlueprintSchematic read(ResourceLocation blueprintId, CompoundTag tag, HolderGetter<Block> blockLookup) {
        ListTag sizeTag = tag.getList("size", Tag.TAG_INT);
        Vec3i size = sizeTag.size() == 3
                ? new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2))
                : Vec3i.ZERO;

        Set<String> unknown = new HashSet<>();
        List<BlockState> palette = readPalette(tag, blockLookup, unknown);

        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        List<CompoundTag> blockEntities = new ArrayList<>();
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
            // Air is kept on purpose: it is how a structure clears the space it occupies, and it is also what
            // an unknown or missing block turns into.
            positions.add(new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2)));
            states.add(palette.get(index));
            blockEntities.add(entry.contains("nbt", Tag.TAG_COMPOUND) ? entry.getCompound("nbt") : null);
        }

        if (!unknown.isEmpty()) {
            CustomBuilding.LOGGER.warn("Structure '{}' uses {} block(s) that do not exist here and will be skipped: {}",
                    blueprintId, unknown.size(), String.join(", ", unknown));
        }

        // Note: the block entity list holds nulls for every block without block entity data, so it must not go
        // through List.copyOf - that rejects null elements.
        return new BlueprintSchematic(blueprintId, size, List.copyOf(positions), List.copyOf(states),
                Collections.unmodifiableList(blockEntities));
    }

    private static List<BlockState> readPalette(CompoundTag tag, HolderGetter<Block> blockLookup, Set<String> unknown) {
        ListTag paletteTag;
        if (tag.contains("palette", Tag.TAG_LIST)) {
            paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        } else {
            ListTag palettes = tag.getList("palettes", Tag.TAG_LIST);
            paletteTag = palettes.isEmpty() ? new ListTag() : palettes.getList(0);
        }

        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(readState(paletteTag.getCompound(i), blockLookup, unknown));
        }
        return palette;
    }

    private static BlockState readState(CompoundTag tag, HolderGetter<Block> blockLookup, Set<String> unknown) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Name"));
        if (id == null) {
            return Blocks.AIR.defaultBlockState();
        }
        ResourceLocation resolved = RENAMES.getOrDefault(id, id);
        Optional<? extends Holder<Block>> holder = blockLookup.get(ResourceKey.create(Registries.BLOCK, resolved));
        if (holder.isEmpty()) {
            unknown.add(id.toString());
            return Blocks.AIR.defaultBlockState();
        }

        BlockState state = holder.get().value().defaultBlockState();
        if (tag.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag properties = tag.getCompound("Properties");
            for (String key : properties.getAllKeys()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                if (property != null) {
                    state = applyProperty(state, property, properties.getString(key));
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(found -> state.setValue(property, found)).orElse(state);
    }
}
