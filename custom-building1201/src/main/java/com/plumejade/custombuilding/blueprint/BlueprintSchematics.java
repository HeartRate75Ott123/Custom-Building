package com.plumejade.custombuilding.blueprint;

import java.io.InputStream;
import java.util.Optional;

import com.plumejade.custombuilding.CustomBuilding;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;

/** Server side helper that turns a blueprint's structure file into a client renderable schematic. */
public final class BlueprintSchematics {
    private BlueprintSchematics() {}

    /** Reads the structure of a blueprint, or {@link BlueprintSchematic#EMPTY} when it cannot be read. */
    public static BlueprintSchematic load(ServerLevel level, ResourceLocation blueprintId) {
        BlueprintDefinition definition = BlueprintDefinitions.byId(blueprintId);
        if (definition == null) {
            return BlueprintSchematic.EMPTY;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return BlueprintSchematic.EMPTY;
        }

        ResourceLocation file = BlueprintPaths.structureFile(definition.structure());
        Optional<Resource> resource = server.getResourceManager().getResource(file);
        if (!resource.isPresent()) {
            return BlueprintSchematic.EMPTY;
        }

        try (InputStream stream = resource.get().open()) {
            CompoundTag tag = NbtIo.readCompressed(stream);
            return BlueprintSchematic.read(blueprintId, tag,
                    level.registryAccess().lookup(Registries.BLOCK).orElseThrow());
        } catch (Exception exception) {
            CustomBuilding.LOGGER.error("Could not read the structure of blueprint '{}'", blueprintId, exception);
            return BlueprintSchematic.EMPTY;
        }
    }
}
