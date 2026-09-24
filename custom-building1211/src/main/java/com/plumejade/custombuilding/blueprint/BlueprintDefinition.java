package com.plumejade.custombuilding.blueprint;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * A fully resolved blueprint.
 *
 * @param id        the datapack file id, e.g. {@code custom_building:initial_house}
 * @param structure the structure template id, loaded from {@code data/<ns>/structure/<path>.nbt}
 * @param preview   the preview image shown in the item tooltip, may be {@code null}; always stretched to 4:3
 * @param texture   the inventory texture of the blueprint item
 * @param name      the item name; a translation key or a plain string (legacy {@code §} colour codes work)
 * @param tooltip   the tooltip lines, same rules as {@link #name}
 * @param size      the size of the structure template; filled in by the reload listener so the client can
 *                  draw the placement outline.  {@link Vec3i#ZERO} when the structure could not be read.
 */
public record BlueprintDefinition(ResourceLocation id,
                                  ResourceLocation structure,
                                  @Nullable ResourceLocation preview,
                                  ResourceLocation texture,
                                  String name,
                                  List<String> tooltip,
                                  Vec3i size) {

    /**
     * The codec used to send definitions from the server to the client.
     */
    public static final Codec<BlueprintDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(BlueprintDefinition::id),
            ResourceLocation.CODEC.fieldOf("structure").forGetter(BlueprintDefinition::structure),
            ResourceLocation.CODEC.optionalFieldOf("preview").forGetter(definition -> Optional.ofNullable(definition.preview())),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(BlueprintDefinition::texture),
            Codec.STRING.fieldOf("name").forGetter(BlueprintDefinition::name),
            Codec.STRING.listOf().optionalFieldOf("tooltip", List.of()).forGetter(BlueprintDefinition::tooltip),
            Codec.INT.listOf().optionalFieldOf("size", List.of(0, 0, 0)).forGetter(definition -> List.of(
                    definition.size().getX(), definition.size().getY(), definition.size().getZ()))
    ).apply(instance, (id, structure, preview, texture, name, tooltip, size) ->
            new BlueprintDefinition(id, structure, preview.orElse(null), texture, name, tooltip, toVec3i(size))));

    public static final Codec<List<BlueprintDefinition>> LIST_CODEC = CODEC.listOf();

    public BlueprintDefinition withSize(Vec3i newSize) {
        return new BlueprintDefinition(id, structure, preview, texture, name, tooltip, newSize);
    }

    /** Turns a configured name / tooltip line into a component. */
    public static MutableComponent textComponent(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return Component.translatable(raw);
    }

    private static Vec3i toVec3i(List<Integer> values) {
        if (values.size() != 3) {
            return Vec3i.ZERO;
        }
        return new Vec3i(values.get(0), values.get(1), values.get(2));
    }
}
