package com.plumejade.custombuilding.client;

import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * A fake world that only contains the blocks of the previewed structure.
 *
 * <p>Used purely for face culling: handing it to {@code Block.shouldRenderFace} makes the ghost cull the
 * faces between its own blocks, so a hollow house really looks hollow instead of a solid lump.</p>
 */
public final class SchematicBlockGetter implements BlockGetter {
    private final ClientLevel level;
    private final Map<BlockPos, BlockState> blocks;

    public SchematicBlockGetter(ClientLevel level, Map<BlockPos, BlockState> blocks) {
        this.level = level;
        this.blocks = blocks;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = this.blocks.get(pos);
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return this.level.getMinBuildHeight();
    }

    @Override
    public int getMaxBuildHeight() {
        return this.level.getMaxBuildHeight();
    }
}
