package com.plumejade.custombuilding.blueprint;

import java.util.List;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;

/**
 * A blueprint exactly as it is written down in {@code data/<namespace>/custom_building/blueprint/<name>.json}.
 *
 * <pre>{@code
 * {
 *   "structure": "custom_building:initial_house",
 *   "preview":   "assets/custom_building/textures/blueprint/initial_house.png",
 *   "texture":   "assets/custom_building/textures/item/initial_house.png",
 *   "name":      "§6初始小屋蓝图",
 *   "tooltip":   ["§a右键地面来使用"]
 * }
 * }</pre>
 */
public record RawBlueprint(String structure,
                           Optional<String> preview,
                           Optional<String> texture,
                           Optional<String> name,
                           List<String> tooltip) {

    /** Allows both {@code "tooltip": "one line"} and {@code "tooltip": ["a", "b"]}. */
    private static final Codec<List<String>> TOOLTIP_CODEC = Codec.either(Codec.STRING, Codec.STRING.listOf())
            .xmap(either -> either.map(List::of, lines -> lines),
                    lines -> lines.size() == 1
                            ? Either.<String, List<String>>left(lines.get(0))
                            : Either.<String, List<String>>right(lines));

    public static final Codec<RawBlueprint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("structure").forGetter(RawBlueprint::structure),
            Codec.STRING.optionalFieldOf("preview").forGetter(RawBlueprint::preview),
            Codec.STRING.optionalFieldOf("texture").forGetter(RawBlueprint::texture),
            Codec.STRING.optionalFieldOf("name").forGetter(RawBlueprint::name),
            TOOLTIP_CODEC.optionalFieldOf("tooltip", List.of()).forGetter(RawBlueprint::tooltip)
    ).apply(instance, RawBlueprint::new));

    /**
     * Resolves all configured paths against the namespace of the datapack file.
     *
     * @param id the id of the blueprint, which is the datapack file id (namespace + file name)
     */
    public BlueprintDefinition resolve(ResourceLocation id) {
        ResourceLocation structure = BlueprintPaths.resolveStructure(this.structure, id);
        ResourceLocation preview = BlueprintPaths.resolveTexture(this.preview.orElse(null), id, false);
        ResourceLocation texture = BlueprintPaths.resolveTexture(this.texture.orElse(null), id, true);
        String displayName = this.name.filter(value -> !value.isBlank()).orElse(id.toString());
        List<String> lines = this.tooltip.stream().filter(line -> line != null && !line.isEmpty()).toList();
        return new BlueprintDefinition(id, structure, preview, texture, displayName, lines, Vec3i.ZERO);
    }
}
