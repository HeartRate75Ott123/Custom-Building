package com.plumejade.custombuilding.blueprint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * The blueprints that are currently configured.
 *
 * <p>The logical server owns the authoritative list and fills it from the datapack through
 * {@code BlueprintReloadListener}.  The client receives the exact same list through
 * {@code BlueprintSyncPayload} whenever the datapacks are (re)loaded, which is what makes a blueprint
 * show up in the creative tab right after {@code /reload}.</p>
 */
public final class BlueprintDefinitions {
    private static volatile List<BlueprintDefinition> serverSide = List.of();
    private static volatile List<BlueprintDefinition> clientSide = List.of();
    private static volatile Map<ResourceLocation, BlueprintDefinition> serverIndex = Map.of();
    private static volatile Map<ResourceLocation, BlueprintDefinition> clientIndex = Map.of();

    private BlueprintDefinitions() {}

    /** Called by the datapack reload listener, server side only. */
    public static void setServerSide(List<BlueprintDefinition> definitions) {
        List<BlueprintDefinition> immutable = List.copyOf(definitions);
        serverSide = immutable;
        serverIndex = index(immutable);
    }

    /** Called when the server pushes its blueprint list to this client. */
    public static void setClientSide(List<BlueprintDefinition> definitions) {
        List<BlueprintDefinition> immutable = List.copyOf(definitions);
        clientSide = immutable;
        clientIndex = index(immutable);
    }

    public static List<BlueprintDefinition> serverSide() {
        return serverSide;
    }

    public static List<BlueprintDefinition> clientSide() {
        return clientSide;
    }

    /**
     * The blueprints shown in the creative tab.  The client list wins; before the first sync (for example
     * in single player, where the client and the server share one JVM) we fall back to the server list so
     * the tab is never unexpectedly empty.
     */
    public static List<BlueprintDefinition> tabEntries() {
        List<BlueprintDefinition> client = clientSide;
        return client.isEmpty() ? serverSide : client;
    }

    @Nullable
    public static BlueprintDefinition byId(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        BlueprintDefinition definition = clientIndex.get(id);
        return definition != null ? definition : serverIndex.get(id);
    }

    private static Map<ResourceLocation, BlueprintDefinition> index(List<BlueprintDefinition> definitions) {
        Map<ResourceLocation, BlueprintDefinition> map = new HashMap<>();
        for (BlueprintDefinition definition : definitions) {
            map.put(definition.id(), definition);
        }
        return Map.copyOf(map);
    }
}
