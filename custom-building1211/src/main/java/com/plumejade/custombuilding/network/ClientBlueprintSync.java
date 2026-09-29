package com.plumejade.custombuilding.network;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.blueprint.BlueprintDefinition;
import com.plumejade.custombuilding.blueprint.BlueprintDefinitions;
import com.plumejade.custombuilding.client.MergedSearchTree;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.searchtree.FullTextSearchTree;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.ClientTooltipFlag;
import net.neoforged.neoforge.client.CreativeModeTabSearchRegistry;

/**
 * Client side half of the blueprint sync.
 *
 * <p>Vanilla caches the built creative tab contents and only rebuilds them when the feature flags, the
 * permissions or the registry access change - none of which happens on a datapack reload.  Rather than
 * invalidating that cache, which would make <em>every</em> tab in the game run its generator again, the mod
 * rebuilds exactly the two tabs it has to: its own, and the search tab, whose generator only concatenates
 * the lists the other tabs have already built.  The creative screen reads {@code getDisplayItems()} every
 * time it is opened, so a freshly configured blueprint shows up the next time the tab is looked at.</p>
 *
 * <p>The creative <em>search</em> has a second, separate cache: the search trees.  Vanilla only fills them
 * from {@code CreativeModeInventoryScreen}, and only while it performs that same full rebuild.  They
 * therefore have to be refilled here - but not by rebuilding them.  Building one means asking every item
 * for its tooltip and sorting the result, which is heavy enough to be noticeable, and the creative screen
 * {@code join}s the tree on the render thread as soon as the player types.  Instead the tree that is
 * already there is kept and merged with a small tree over this mod's own items; see
 * {@link MergedSearchTree}.  Pinyin search mods such as Just Enough Characters replace the implementation
 * of {@code SearchTree.plainText}, so the small tree gets their matching for free.</p>
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

    /** Called every client tick; refreshes the creative tabs once the client is ready for it. */
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
            CreativeModeTab searchTab = CreativeModeTabs.searchTab();
            if (searchTab.getDisplayItems().isEmpty()) {
                // The tabs have never been built, so the player has not opened the creative screen yet.
                // Vanilla builds all of them (and their search trees) itself the first time it is opened,
                // and that build reads the blueprint list we have just received, so there is nothing to do
                // and nothing to gain from touching the cached contents early.
                return;
            }

            HolderLookup.Provider registries = minecraft.level.registryAccess();
            CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                    minecraft.player.connection.enabledFeatures(),
                    minecraft.player.canUseGameMasterBlocks() && minecraft.options.operatorItemsTab().get(),
                    registries);

            // Our own tab is the only generator that has to run again.  The search tab is rebuilt right
            // after it so that its item list - which is what the search tree is built from - contains the
            // new blueprints too; its generator only collects what every other tab has already built.
            long started = System.nanoTime();
            CreativeModeTab customBuildingTab = CustomBuilding.CUSTOM_BUILDING_TAB.get();
            customBuildingTab.buildContents(parameters);
            searchTab.buildContents(parameters);

            List<ItemStack> blueprints = List.copyOf(customBuildingTab.getDisplayItems());
            refreshSearchTree(minecraft, registries, searchTab, blueprints);

            // Deliberately at INFO: a datapack reload should be invisible, and this is the line that says
            // whether it still is.
            CustomBuilding.LOGGER.info("Refreshed the custom building tab and the creative search tree in {} ms ({} blueprints)",
                    (System.nanoTime() - started) / 1_000_000L, blueprints.size());
        } catch (Exception exception) {
            CustomBuilding.LOGGER.error("Failed to refresh the creative mode tabs", exception);
        }
    }

    /**
     * Makes the newly configured blueprints findable again, without rebuilding the creative search tree.
     *
     * <p>The tree is only replaced once a small tree holding this mod's items is ready, which takes
     * microseconds instead of the hundreds of milliseconds a full rebuild needs - and because the tree that
     * is served in the meantime is already complete, the creative screen never has to wait for a build that
     * is still running.</p>
     */
    private static void refreshSearchTree(Minecraft minecraft, HolderLookup.Provider registries,
                                          CreativeModeTab searchTab, List<ItemStack> blueprints) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || blueprints.isEmpty()) {
            return;
        }

        SessionSearchTrees.Key key = CreativeModeTabSearchRegistry.getNameSearchKey(searchTab);
        CompletableFuture<SearchTree<ItemStack>> current = CreativeModeTabSearchRegistry.getNameSearchTree(key);
        if (!current.isDone() || current.isCompletedExceptionally()) {
            // A full build is still running, or the last one was cancelled.  Do what the creative screen
            // does instead of racing it.
            SessionSearchTrees searchTrees = connection.searchTrees();
            searchTrees.updateCreativeTooltips(registries, List.copyOf(searchTab.getDisplayItems()), key);
            return;
        }

        Item.TooltipContext context = Item.TooltipContext.of(registries);
        TooltipFlag flag = ClientTooltipFlag.of(TooltipFlag.Default.NORMAL.asCreative());
        SearchTree<ItemStack> added = new FullTextSearchTree<>(
                stack -> tooltipLines(stack, context, flag),
                stack -> stack.getItemHolder().unwrapKey().map(ResourceKey::location).stream(),
                blueprints);

        Set<ItemStack> configured = ItemStackLinkedSet.createTypeAndComponentsSet();
        configured.addAll(blueprints);
        CreativeModeTabSearchRegistry.putNameSearchTree(key, CompletableFuture.completedFuture(
                new MergedSearchTree(current.getNow(SearchTree.empty()), added, configured)));
    }

    /** The text vanilla indexes an item under: its tooltip, with the colour codes stripped. */
    private static Stream<String> tooltipLines(ItemStack stack, Item.TooltipContext context, TooltipFlag flag) {
        return stack.getTooltipLines(context, null, flag).stream()
                .map(line -> ChatFormatting.stripFormatting(line.getString()).trim())
                .filter(line -> !line.isEmpty());
    }
}
