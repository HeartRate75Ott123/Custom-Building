package com.plumejade.custombuilding.network;

import java.util.List;

import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.blueprint.BlueprintDefinition;
import com.plumejade.custombuilding.blueprint.BlueprintDefinitions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.CreativeModeTabSearchRegistry;

/**
 * Client side half of the blueprint sync.
 *
 * <p>Vanilla caches the built creative tab contents and only rebuilds them when the feature flags, the
 * permissions or the registry access change - none of which happens on a datapack reload.  So the cache is
 * invalidated by hand and the tabs are rebuilt, which is exactly what KubeJS does after its startup scripts
 * are reloaded.</p>
 *
 * <p>The creative <em>search</em> has a second, separate cache: the search trees in
 * {@link SessionSearchTrees}.  Vanilla only refills them from {@code CreativeModeInventoryScreen}, and only
 * when its own "do the tabs need rebuilding" check reports a change.  Since the tab cache is refreshed here
 * first, that check never reports a change again, so the trees would stay empty and typing anything into the
 * creative search box would return nothing at all - in any language, pinyin included, because pinyin search
 * mods such as Just Enough Characters only replace the tree implementation, not the tree contents.  The trees
 * therefore have to be refilled here, exactly the way vanilla does it.</p>
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
        if (resources.getResource(texture).isEmpty()) {
            CustomBuilding.LOGGER.warn("Blueprint '{}' points at {} '{}' but 'assets/{}/{}' does not exist",
                    blueprintId, field, texture, texture.getNamespace(), texture.getPath());
        }
    }

    /** Called every client tick; rebuilds the creative tab once the client is ready for it. */
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
            CreativeModeTabs.CACHED_PARAMETERS = null;
            boolean rebuilt = CreativeModeTabs.tryRebuildTabContents(
                    minecraft.player.connection.enabledFeatures(),
                    minecraft.player.canUseGameMasterBlocks() && minecraft.options.operatorItemsTab().get(),
                    registries);
            if (rebuilt) {
                rebuildSearchTrees(minecraft, registries);
            }
        } catch (Exception exception) {
            CustomBuilding.LOGGER.error("Failed to rebuild the creative mode tabs", exception);
        }
    }

    /**
     * Refills the creative search trees for every tab that has a search bar, mirroring
     * {@code CreativeModeInventoryScreen#tryRebuildTabContents}.
     */
    private static void rebuildSearchTrees(Minecraft minecraft, HolderLookup.Provider registries) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return;
        }
        SessionSearchTrees searchTrees = connection.searchTrees();
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (!tab.hasSearchBar()) {
                continue;
            }
            List<ItemStack> items = List.copyOf(tab.getDisplayItems());
            searchTrees.updateCreativeTooltips(registries, items, CreativeModeTabSearchRegistry.getNameSearchKey(tab));
            searchTrees.updateCreativeTags(items, CreativeModeTabSearchRegistry.getTagSearchKey(tab));
        }
    }
}
