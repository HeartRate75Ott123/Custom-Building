package com.plumejade.custombuilding.blueprint;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.network.BlueprintBuildPacket;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Builds the structure of a blueprint and reports the result, mirroring the wording and colouring used by
 * MC-Prefab: yellow system messages when a structure cannot be built, and a green one when it can.
 *
 * <p>The blocks are placed straight from {@link BlueprintSchematic} rather than through vanilla's
 * {@code StructureTemplate}, for two reasons: the schematic reader tolerates blocks that do not exist in this
 * version - they become air, so a datapack written for another version, or one referencing optional mods,
 * still builds - and the ghost preview shows exactly the same block list that is placed.</p>
 */
public final class BlueprintPlacer {
    private BlueprintPlacer() {}

    /** Called on the server when the player confirms the build in the blueprint panel. */
    public static void build(ServerPlayer player, ResourceLocation blueprintId, BlockPos pos, Direction face,
                             Direction requestedFacing, InteractionHand hand) {
        ServerLevel level = player.serverLevel();
        ItemStack stack = player.getItemInHand(hand);

        // The player may have swapped items while the panel was open.
        if (!stack.is(CustomBuilding.BLUEPRINT.get()) || !BlueprintBuildPacket.matches(stack, blueprintId)) {
            return;
        }

        BlueprintDefinition definition = BlueprintDefinitions.byId(blueprintId);
        if (definition == null) {
            sendMessage(player, Component.translatable("message.custom_building.build.unknown_blueprint",
                    blueprintId.toString()), ChatFormatting.YELLOW);
            return;
        }

        BlueprintSchematic schematic = BlueprintSchematics.load(level, definition.id());
        if (schematic.isEmpty()) {
            sendMessage(player, Component.translatable("message.custom_building.build.missing_structure",
                    definition.structure().toString()), ChatFormatting.YELLOW);
            return;
        }

        Direction facing = requestedFacing.getAxis().isHorizontal() ? requestedFacing : Direction.SOUTH;
        Rotation rotation = BlueprintRotations.rotationFor(facing);
        BlockPos target = pos.relative(face);
        BlockPos origin = BlueprintRotations.originFor(target, schematic.size(), facing);
        MinecraftServer server = level.getServer();

        // Work out every destination first, so the protection check can refuse the whole build before
        // anything is changed.
        List<BlockPos> destinations = new ArrayList<>(schematic.positions().size());
        for (BlockPos relative : schematic.positions()) {
            BlockPos world = origin.offset(StructureTemplate.transform(relative, Mirror.NONE, rotation, BlockPos.ZERO));
            BlockState existing = level.getBlockState(world);
            if (!existing.isAir()) {
                if (existing.getDestroySpeed(level, world) < 0.0F) {
                    sendMessage(player, Component.translatable("message.custom_building.build.nobuild_unbreakable",
                            existing.getBlock().getName(), world.getX(), world.getY(), world.getZ()), ChatFormatting.YELLOW);
                    return;
                }
                if (server != null && server.isUnderSpawnProtection(level, world, player)) {
                    sendMessage(player, Component.translatable("message.custom_building.build.nobuild_spawn_protection",
                            existing.getBlock().getName(), world.getX(), world.getY(), world.getZ()), ChatFormatting.YELLOW);
                    return;
                }
            }
            destinations.add(world);
        }

        // Blocks that this version does not know about were turned into air by the schematic reader, so they
        // clear the spot they occupy instead of leaving the terrain behind.
        int changed = 0;
        try {
            for (int i = 0; i < destinations.size(); i++) {
                BlockPos world = destinations.get(i);
                BlockState state = schematic.states().get(i).rotate(rotation);
                if (level.setBlock(world, state, Block.UPDATE_ALL)) {
                    changed++;
                }
                CompoundTag blockEntityTag = schematic.blockEntity(i);
                if (blockEntityTag != null && !state.isAir()) {
                    loadBlockEntity(level, world, blockEntityTag);
                }
            }
        } catch (Exception exception) {
            CustomBuilding.LOGGER.error("Failed to build blueprint '{}' at {}", definition.id(), origin, exception);
            changed = 0;
        }

        if (changed == 0) {
            sendMessage(player, Component.translatable("message.custom_building.build.failed",
                    definition.structure().toString()), ChatFormatting.YELLOW);
            return;
        }

        level.playSound(null, target, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.2F);
        sendMessage(player, Component.translatable("message.custom_building.build.success"), ChatFormatting.GREEN);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private static void loadBlockEntity(ServerLevel level, BlockPos pos, CompoundTag tag) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        try {
            CompoundTag copy = tag.copy();
            copy.putInt("x", pos.getX());
            copy.putInt("y", pos.getY());
            copy.putInt("z", pos.getZ());
            blockEntity.load(copy);
            blockEntity.setChanged();
        } catch (Exception exception) {
            CustomBuilding.LOGGER.warn("Could not restore the block entity data at {}", pos, exception);
        }
    }

    /**
     * Where the structure would end up, used by the client to draw the placement outline.
     *
     * @return the north-west bottom corner of the rotated bounding box, or {@code null} when the size is unknown
     */
    @Nullable
    public static BlockPos outlineOrigin(BlockPos clickedPos, Direction face, Direction facing, Vec3i templateSize) {
        if (templateSize.equals(Vec3i.ZERO)) {
            return null;
        }
        return BlueprintRotations.originFor(clickedPos.relative(face), templateSize, facing);
    }

    public static void sendMessage(ServerPlayer player, Component message, ChatFormatting colour) {
        player.sendSystemMessage(message.copy().withStyle(colour));
    }
}
