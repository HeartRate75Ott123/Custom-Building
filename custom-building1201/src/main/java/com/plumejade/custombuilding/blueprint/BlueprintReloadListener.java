package com.plumejade.custombuilding.blueprint;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.plumejade.custombuilding.CustomBuilding;

import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Reads every blueprint from {@code data/<namespace>/custom_building/blueprint/<name>.json}.
 *
 * <p>Files that do not end in {@code .json} are ignored by {@link SimpleJsonResourceReloadListener}, which is
 * why the shipped example template is called {@code blueprint_template.txt}.</p>
 */
public class BlueprintReloadListener extends SimpleJsonResourceReloadListener {
    /** The datapack directory blueprints are read from. */
    public static final String DIRECTORY = CustomBuilding.MODID + "/blueprint";

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    public BlueprintReloadListener() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> blueprints, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<BlueprintDefinition> definitions = new ArrayList<>();

        blueprints.forEach((id, json) -> {
            try {
                RawBlueprint raw = RawBlueprint.CODEC.parse(JsonOps.INSTANCE, json)
                        .result()
                        .orElseThrow(() -> new IllegalArgumentException("Invalid blueprint definition"));
                definitions.add(withStructureSize(resourceManager, raw.resolve(id)));
            } catch (Exception exception) {
                CustomBuilding.LOGGER.error("Skipping invalid custom building blueprint '{}'", id, exception);
            }
        });

        // Deterministic order keeps the creative tab stable between reloads.
        definitions.sort(Comparator.comparing(definition -> definition.id().toString()));
        BlueprintDefinitions.setServerSide(definitions);

        CustomBuilding.LOGGER.info("Loaded {} custom building blueprint(s)", definitions.size());
    }

    /**
     * Reads the {@code size} tag straight out of the structure nbt file.  Only the size is needed - loading a
     * full {@code StructureTemplate} would require a block lookup - and it lets the client draw the placement
     * outline without having to read the datapack itself.
     *
     * <p>Textures live under {@code assets/} and cannot be seen from the server's resource manager, so they are
     * validated on the client instead.</p>
     */
    private static BlueprintDefinition withStructureSize(ResourceManager resourceManager, BlueprintDefinition definition) {
        ResourceLocation file = BlueprintPaths.structureFile(definition.structure());
        Optional<Resource> resource = resourceManager.getResource(file);
        if (!resource.isPresent()) {
            CustomBuilding.LOGGER.warn("Blueprint '{}' points at structure '{}' but 'data/{}/structure/{}.nbt' does not exist",
                    definition.id(), definition.structure(),
                    definition.structure().getNamespace(), definition.structure().getPath());
            return definition;
        }

        try (InputStream stream = resource.get().open()) {
            CompoundTag tag = NbtIo.readCompressed(stream);
            ListTag size = tag.getList("size", Tag.TAG_INT);
            if (size.size() == 3) {
                return definition.withSize(new Vec3i(size.getInt(0), size.getInt(1), size.getInt(2)));
            }
            CustomBuilding.LOGGER.warn("Structure '{}' of blueprint '{}' has no valid size tag",
                    definition.structure(), definition.id());
        } catch (Exception exception) {
            CustomBuilding.LOGGER.warn("Could not read the size of structure '{}' for blueprint '{}'",
                    definition.structure(), definition.id(), exception);
        }
        return definition;
    }
}
