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
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
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
        long started = System.nanoTime();
        List<BlueprintDefinition> definitions = new ArrayList<>();

        blueprints.forEach((id, json) -> {
            try {
                RawBlueprint raw = RawBlueprint.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                definitions.add(withStructureSize(resourceManager, raw.resolve(id)));
            } catch (Exception exception) {
                CustomBuilding.LOGGER.error("Skipping invalid custom building blueprint '{}'", id, exception);
            }
        });

        // Deterministic order keeps the creative tab stable between reloads.
        definitions.sort(Comparator.comparing(definition -> definition.id().toString()));
        BlueprintDefinitions.setServerSide(definitions);

        CustomBuilding.LOGGER.info("Loaded {} custom building blueprint(s) in {} ms",
                definitions.size(), (System.nanoTime() - started) / 1_000_000L);
    }

    /**
     * Peeks at the {@code size} tag of the structure nbt file without loading the structure.  Only the size
     * is needed - it lets the client draw the placement outline before the blocks arrive - and reading the
     * whole file would be a reload cost that grows with every block of every building: a full parse
     * decompresses the file and allocates a tag for every palette entry and block index, only to throw
     * away everything but three integers.
     *
     * <p>Vanilla writes {@code size} as the first field of a structure file, so the streaming visitor reads
     * a handful of bytes and halts.  Files whose fields come in another order are still skipped
     * structurally - the bytes advance, no tags are built - and cost a fraction of a full parse.  This is
     * the same {@code CollectFields}/{@code parseCompressed} pattern vanilla uses to scan chunk and level
     * data without loading it ({@code IOWorker}, {@code LevelStorageSource}).</p>
     *
     * <p>Textures live under {@code assets/} and cannot be seen from the server's resource manager, so they
     * are validated on the client instead.</p>
     */
    private static BlueprintDefinition withStructureSize(ResourceManager resourceManager, BlueprintDefinition definition) {
        ResourceLocation file = BlueprintPaths.structureFile(definition.structure());
        Optional<Resource> resource = resourceManager.getResource(file);
        if (resource.isEmpty()) {
            CustomBuilding.LOGGER.warn("Blueprint '{}' points at structure '{}' but 'data/{}/structure/{}.nbt' does not exist",
                    definition.id(), definition.structure(),
                    definition.structure().getNamespace(), definition.structure().getPath());
            return definition;
        }

        try (InputStream stream = resource.get().open()) {
            CollectFields visitor = new CollectFields(new FieldSelector(ListTag.TYPE, "size"));
            NbtIo.parseCompressed(stream, visitor, NbtAccounter.unlimitedHeap());
            if (!(visitor.getResult() instanceof CompoundTag tag)) {
                CustomBuilding.LOGGER.warn("Structure '{}' of blueprint '{}' does not start with a compound tag",
                        definition.structure(), definition.id());
                return definition;
            }
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
