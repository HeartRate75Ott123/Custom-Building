package com.plumejade.custombuilding.network;

import java.util.List;

import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.blueprint.BlueprintDefinition;
import com.plumejade.custombuilding.blueprint.BlueprintDefinitions;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Client side half of the blueprint sync.
 *
 * <p>Vanilla caches the built creative tab contents and only rebuilds them when the feature flags, the
 * permissions or the registry access change - none of which happens on a datapack reload.  Instead of
 * invalidating that private cache, the mod rebuilds its own tab through the public
 * {@link CreativeModeTab#buildContents} method; the creative screen repopulates its item list from
 * {@code getDisplayItems()} every time it is opened, so newly configured blueprints show up the next time
 * the tab is looked at.</p>
 */
public final class ClientBlueprintSync {
    private static volatile boolean dirty;

    private ClientBlueprintSync() {}

    /** Called on the client thread when the server pushes a new blueprint list. */
    public static void accept(List<BlueprintDefinition> definitions) {
        BlueprintDefinitions.setClientSide(definitions);
        validateTextures(definitions);
        dirty = true;
    }

    /**
     * Warns about textures a blueprint points at but that do not exist.  Only the client can do this: the
     * server's resource manager exposes {@code data/} and cannot see {@code assets/}.
     */
    private static void validateTextures(List<BlueprintDefinition> definitions) {
        try {
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            for (BlueprintDefinition definition : definitions) {
                warnMissing(resources, definition.id(), "texture", definition.texture());
                if (definition.preview() != null) {
                    warnMissing(resources, definition.id(), "preview", definition.preview());
                }
            }
        } catch (Exception exception) {
            CustomBuilding.LOGGER.debug("Skipped blueprint texture validation", exception);
        }
    }

    private static void warnMissing(ResourceManager resources, ResourceLocation blueprintId, String field, ResourceLocation texture) {
        if (!resources.getResource(texture).isPresent()) {
            CustomBuilding.LOGGER.warn("Blueprint '{}' points at {} '{}' but 'assets/{}/{}' does not exist",
                    blueprintId, field, texture, texture.getNamespace(), texture.getPath());
        }
    }

    /** Called every client tick; rebuilds our creative tab once the client is ready for it. */
    public static void tick() {
        if (!dirty) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        dirty = false;

        try {
            HolderLookup.Provider registries = minecraft.level.registryAccess();
            CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                    minecraft.player.connection.enabledFeatures(),
                    minecraft.player.canUseGameMasterBlocks() && minecraft.options.operatorItemsTab().get(),
                    registries);
            CustomBuilding.CUSTOM_BUILDING_TAB.get().buildContents(parameters);
        } catch (Exception exception) {
            CustomBuilding.LOGGER.error("Failed to rebuild the custom building creative tab", exception);
        }
    }
}
