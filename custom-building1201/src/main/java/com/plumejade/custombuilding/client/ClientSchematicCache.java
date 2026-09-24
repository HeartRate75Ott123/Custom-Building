package com.plumejade.custombuilding.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.blueprint.BlueprintSchematic;
import com.plumejade.custombuilding.network.CBNetwork;
import com.plumejade.custombuilding.network.SchematicRequestPacket;

import net.minecraft.resources.ResourceLocation;

/** Client side store of the structures that have been fetched from the server. */
public final class ClientSchematicCache {
    private static final Map<ResourceLocation, BlueprintSchematic> CACHE = new HashMap<>();
    private static final Set<ResourceLocation> REQUESTED = new HashSet<>();

    private ClientSchematicCache() {}

    /** Asks the server for a schematic, unless it is already here or already on its way. */
    public static void request(ResourceLocation blueprintId) {
        if (CACHE.containsKey(blueprintId) || !REQUESTED.add(blueprintId)) {
            return;
        }
        CBNetwork.sendToServer(new SchematicRequestPacket(blueprintId));
    }

    @Nullable
    public static BlueprintSchematic get(ResourceLocation blueprintId) {
        return CACHE.get(blueprintId);
    }

    /** Called when the server answers a request. */
    public static void accept(BlueprintSchematic schematic) {
        if (schematic.isEmpty()) {
            CustomBuilding.LOGGER.warn("The server returned no blocks for blueprint '{}'", schematic.blueprintId());
            REQUESTED.remove(schematic.blueprintId());
            BlueprintPreviewState.onSchematicReady(schematic.blueprintId());
            return;
        }
        CACHE.put(schematic.blueprintId(), schematic);
        BlueprintPreviewState.onSchematicReady(schematic.blueprintId());
    }

    /** Forgets everything, for example when leaving the world. */
    public static void clear() {
        CACHE.clear();
        REQUESTED.clear();
    }
}
