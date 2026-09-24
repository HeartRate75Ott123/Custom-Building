package com.plumejade.custombuilding.network;

import java.util.function.Supplier;

import com.plumejade.custombuilding.blueprint.BlueprintHelper;
import com.plumejade.custombuilding.blueprint.BlueprintPlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * Sent when the player presses "建造！" in the blueprint panel.
 */
public class BlueprintBuildPacket {
    private final ResourceLocation blueprintId;
    private final BlockPos pos;
    private final Direction face;
    private final Direction facing;
    private final InteractionHand hand;

    public BlueprintBuildPacket(ResourceLocation blueprintId, BlockPos pos, Direction face, Direction facing, InteractionHand hand) {
        this.blueprintId = blueprintId;
        this.pos = pos;
        this.face = face;
        this.facing = facing;
        this.hand = hand;
    }

    public BlueprintBuildPacket(FriendlyByteBuf buffer) {
        this.blueprintId = buffer.readResourceLocation();
        this.pos = buffer.readBlockPos();
        this.face = buffer.readEnum(Direction.class);
        this.facing = buffer.readEnum(Direction.class);
        this.hand = buffer.readEnum(InteractionHand.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(this.blueprintId);
        buffer.writeBlockPos(this.pos);
        buffer.writeEnum(this.face);
        buffer.writeEnum(this.facing);
        buffer.writeEnum(this.hand);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            BlueprintPlacer.build(player, this.blueprintId, this.pos, this.face, this.facing, this.hand);
        }
    }

    /** {@return whether the given stack is the blueprint this packet was built for} */
    public static boolean matches(ItemStack stack, ResourceLocation blueprintId) {
        CompoundTag tag = stack.getTag();
        return tag != null
                && tag.contains(BlueprintHelper.TAG_BLUEPRINT, Tag.TAG_STRING)
                && blueprintId.toString().equals(tag.getString(BlueprintHelper.TAG_BLUEPRINT));
    }
}
