package com.plumejade.custombuilding.blueprint;

import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.component.CBDataComponents;
import com.plumejade.custombuilding.network.BlueprintBuildPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Places the structure of a blueprint and reports the result, mirroring the wording and colouring used by
 * MC-Prefab 1.21.1: yellow system messages when a structure cannot be built, and a green one when it can.
 */
public final class BlueprintPlacer {
    private BlueprintPlacer() {}

    /** Called on the server when the player confirms the build in the blueprint panel. */
    public static void build(ServerPlayer player, BlueprintBuildPayload request) {
        ServerLevel level = player.serverLevel();
        ItemStack stack = player.getItemInHand(request.hand());

        // The player may have swapped items while the panel was open.
        if (!stack.is(CustomBuilding.BLUEPRINT.get())
                || !request.blueprintId().equals(stack.get(CBDataComponents.BLUEPRINT.get()))) {
            return;
        }

        BlueprintDefinition definition = BlueprintDefinitions.byId(request.blueprintId());
        if (definition == null) {
            sendMessage(player, Component.translatable("message.custom_building.build.unknown_blueprint",
                    request.blueprintId().toString()), ChatFormatting.YELLOW);
            return;
        }

        StructureTemplate template = loadTemplate(level, definition);
        if (template == null) {
            sendMessage(player, Component.translatable("message.custom_building.build.missing_structure",
                    definition.structure().toString()), ChatFormatting.YELLOW);
            return;
        }

        Direction facing = request.facing().getAxis().isHorizontal() ? request.facing() : Direction.SOUTH;
        Rotation rotation = BlueprintRotations.rotationFor(facing);
        Vec3i templateSize = template.getSize();
        Vec3i footprint = BlueprintRotations.rotatedSize(templateSize, rotation);

        // The footprint always starts at the block in front of the clicked face, whatever the rotation.
        BlockPos target = request.pos().relative(request.face());

        // Protected / unbreakable blocks block the build, just like in MC-Prefab.
        BlockPos max = target.offset(footprint.getX() - 1, footprint.getY() - 1, footprint.getZ() - 1);
        for (BlockPos pos : BlockPos.betweenClosed(target, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.getDestroySpeed(level, pos) < 0.0F) {
                sendMessage(player, Component.translatable("message.custom_building.build.nobuild_unbreakable",
                        state.getBlock().getName(), pos.getX(), pos.getY(), pos.getZ()), ChatFormatting.YELLOW);
                return;
            }
            if (level.getServer() != null && level.getServer().isUnderSpawnProtection(level, pos, player)) {
                sendMessage(player, Component.translatable("message.custom_building.build.nobuild_spawn_protection",
                        state.getBlock().getName(), pos.getX(), pos.getY(), pos.getZ()), ChatFormatting.YELLOW);
                return;
            }
        }

        BlockPos origin = BlueprintRotations.originFor(target, templateSize, facing);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setRotationPivot(BlockPos.ZERO)
                .setIgnoreEntities(false);
        RandomSource random = RandomSource.create(level.getRandom().nextLong());

        boolean placed;
        try {
            placed = template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_ALL);
        } catch (Exception exception) {
            CustomBuilding.LOGGER.error("Failed to place blueprint '{}' at {}", definition.id(), origin, exception);
            placed = false;
        }

        if (!placed) {
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

    @Nullable
    private static StructureTemplate loadTemplate(ServerLevel level, BlueprintDefinition definition) {
        try {
            // get() instead of getOrCreate(): getOrCreate() happily returns an empty template for a
            // missing file, which would silently do nothing.
            StructureTemplate template = level.getStructureManager().get(definition.structure()).orElse(null);
            if (template == null || template.getSize().equals(Vec3i.ZERO)) {
                return null;
            }
            return template;
        } catch (Exception exception) {
            CustomBuilding.LOGGER.error("Failed to load structure '{}' for blueprint '{}'",
                    definition.structure(), definition.id(), exception);
            return null;
        }
    }

    public static void sendMessage(ServerPlayer player, Component message, ChatFormatting colour) {
        player.sendSystemMessage(message.copy().withStyle(colour));
    }
}
