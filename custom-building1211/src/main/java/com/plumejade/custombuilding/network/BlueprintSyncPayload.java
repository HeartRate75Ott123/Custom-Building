package com.plumejade.custombuilding.network;

import java.util.List;

import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.blueprint.BlueprintDefinition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Pushes the complete list of configured blueprints to a client.
 *
 * <p>Sent when a player joins and after every datapack reload.  The client rebuilds the creative tab
 * contents as soon as it arrives, which is what makes "/reload then look in the tab" work.</p>
 */
public record BlueprintSyncPayload(List<BlueprintDefinition> definitions) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlueprintSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CustomBuilding.MODID, "blueprint_sync"));

    public static final StreamCodec<FriendlyByteBuf, BlueprintSyncPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> buffer.writeNbt(payload.write()), BlueprintSyncPayload::read);

    private static final String DEFINITIONS_KEY = "definitions";

    private static BlueprintSyncPayload read(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        if (tag == null) {
            return new BlueprintSyncPayload(List.of());
        }
        return new BlueprintSyncPayload(BlueprintDefinition.LIST_CODEC
                .parse(NbtOps.INSTANCE, tag.getList(DEFINITIONS_KEY, Tag.TAG_COMPOUND))
                .result()
                .orElseGet(List::of));
    }

    private CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        Tag encoded = BlueprintDefinition.LIST_CODEC
                .encodeStart(NbtOps.INSTANCE, this.definitions)
                .result()
                .orElseGet(ListTag::new);
        tag.put(DEFINITIONS_KEY, encoded);
        return tag;
    }

    @Override
    public CustomPacketPayload.Type<BlueprintSyncPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientBlueprintSync.accept(this.definitions));
    }
}
