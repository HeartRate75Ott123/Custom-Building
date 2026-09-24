package com.plumejade.custombuilding.network;

import java.util.List;
import java.util.function.Supplier;

import com.plumejade.custombuilding.blueprint.BlueprintDefinition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Pushes the complete list of configured blueprints to a client.
 *
 * <p>Sent when a player joins and after every datapack reload.  The client rebuilds the creative tab
 * contents as soon as it arrives, which is what makes "/reload then look in the tab" work.</p>
 */
public class BlueprintSyncPacket {
    private static final String DEFINITIONS_KEY = "definitions";

    private final List<BlueprintDefinition> definitions;

    public BlueprintSyncPacket(List<BlueprintDefinition> definitions) {
        this.definitions = definitions;
    }

    public BlueprintSyncPacket(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        if (tag == null) {
            this.definitions = List.of();
        } else {
            this.definitions = BlueprintDefinition.LIST_CODEC
                    .parse(NbtOps.INSTANCE, tag.getList(DEFINITIONS_KEY, Tag.TAG_COMPOUND))
                    .result()
                    .orElseGet(List::of);
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        CompoundTag tag = new CompoundTag();
        Tag encoded = BlueprintDefinition.LIST_CODEC
                .encodeStart(NbtOps.INSTANCE, this.definitions)
                .result()
                .orElseGet(ListTag::new);
        tag.put(DEFINITIONS_KEY, encoded);
        buffer.writeNbt(tag);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ClientBlueprintSync.accept(this.definitions);
    }
}
