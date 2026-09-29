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
 * therefore have to be refilled here - but not by rebuilding them.  Building one means asking every item in
 * the game for its tooltip and sorting the result, which is heavy enough to be noticeable, and the creative
 * screen {@code join}s the tree on the render thread as soon as the player types.  Instead the tree that is
 * already there is kept and merged with a small tree over this mod's own items; see
 * {@link MergedSearchTree}.  Pinyin search mods such as Just Enough Characters replace the implementation of
 * {@code SearchTree.plainText}, so the small tree gets their matching for free.</p>
 */
public final class ClientBlueprintSync {
    private static volatile boolean dirty;

    /**
     * The future this class last put into the registry, and the tree the merge was based on.
     *
     * <p>Everything here runs on the client thread - the tick that maintains it and the render thread that
     * searches the tree are the same thread - so no synchronisation is needed.  The identity of the future
     * is what tells us whether the tree in the registry is still ours, and the base lets a later merge
     * flatten instead of stacking another layer on our own previous merge.</p>
     */
    private static CompletableFuture<SearchTree<ItemStack>> installedFuture;
    private static SearchTree<ItemStack> installedBase;

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

    /** Called every client tick: applies a new blueprint list, and otherwise guards the search tree. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (!dirty) {
            try {
                keepSearchTreeCurrent(minecraft);
            } catch (Exception exception) {
                // Loading a new world cancels trees out from under us; give up on this one instead of
                // retrying - and failing - every tick.  The next blueprint sync merges again.
                installedFuture = null;
                installedBase = null;
                CustomBuilding.LOGGER.error("Failed to keep the creative search tree in sync", exception);
            }
            return;
        }

        dirty = false;
        try {
            refreshCreativeTabs(minecraft);
        } catch (Exception exception) {
            CustomBuilding.LOGGER.error("Failed to refresh the creative mode tabs", exception);
        }
    }

    /** Rebuilds the two tabs this mod is responsible for, then lets the search tree catch up. */
    private static void refreshCreativeTabs(Minecraft minecraft) {
        CreativeModeTab searchTab = CreativeModeTabs.searchTab();
        if (searchTab.getDisplayItems().isEmpty()) {
            // The tabs have never been built, so the player has not opened the creative screen yet.
            // Vanilla builds all of them (and their search trees) itself the first time it is opened, and
            // that build reads the blueprint list we have just received, so there is nothing to do and
            // nothing to gain from touching the cached contents early.
            return;
        }

        long started = System.nanoTime();

        HolderLookup.Provider registries = minecraft.level.registryAccess();
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                minecraft.player.connection.enabledFeatures(),
                minecraft.player.canUseGameMasterBlocks() && minecraft.options.operatorItemsTab().get(),
                registries);

        // Our own tab is the only generator that has to run again.  The search tab is rebuilt right after it
        // so that its item list - which is what the search tree is built from - contains the new blueprints
        // too; its generator only collects what every other tab has already built.
        CreativeModeTab customBuildingTab = CustomBuilding.CUSTOM_BUILDING_TAB.get();
        customBuildingTab.buildContents(parameters);
        searchTab.buildContents(parameters);

        SessionSearchTrees.Key key = CreativeModeTabSearchRegistry.getNameSearchKey(searchTab);
        if (key == null) {
            CustomBuilding.LOGGER.warn("The creative search tab has no search key, blueprints cannot be made searchable");
            return;
        }

        List<ItemStack> blueprints = List.copyOf(customBuildingTab.getDisplayItems());
        mergeSearchTree(minecraft, key, searchTab, blueprints);

        // Deliberately at INFO: a datapack reload should be invisible, and this is the line that says
        // whether it still is.
        CustomBuilding.LOGGER.info("Refreshed the custom building tab and the creative search tree in {} ms ({} blueprints)",
                (System.nanoTime() - started) / 1_000_000L, blueprints.size());
    }

    /**
     * Reinstates this mod's items in the creative search tree whenever it is no longer the tree we put
     * there.
     *
     * <p>A language change is the case that matters: vanilla then rebuilds the tree from the item list its
     * reloader captured when the tree was last built, and that list predates every blueprint configured
     * since - so the blueprints would silently stop being findable.  (A reloader only exists once the
     * creative screen has built the tabs, which is also the only way this class can have merged before, so
     * that really is the case we are catching here.)  Instead of hooking the language change, the merge is
     * simply redone: the small tree is rebuilt on the spot, so this mod's items end up indexed in whatever
     * language is current now.</p>
     */
    private static void keepSearchTreeCurrent(Minecraft minecraft) {
        if (installedFuture == null) {
            // Nothing of ours is in the tree, so there is nothing to keep alive.
            return;
        }
        if (minecraft.getConnection() == null) {
            return;
        }
        CreativeModeTab searchTab = CreativeModeTabs.searchTab();
        SessionSearchTrees.Key key = CreativeModeTabSearchRegistry.getNameSearchKey(searchTab);
        if (key == null || CreativeModeTabSearchRegistry.getNameSearchTree(key) == installedFuture) {
            return;
        }
        mergeSearchTree(minecraft, key, searchTab, blueprintsOf());
    }

    /**
     * Merges a small tree over this mod's items into whatever tree the creative inventory searches.
     *
     * <p>The tree is only ever replaced by a future that is already complete, so the creative screen can
     * never end up waiting for a build: either there is a finished tree to search (ours), or - while
     * vanilla's is still running - the tree that was serving searches before.</p>
     */
    private static void mergeSearchTree(Minecraft minecraft, SessionSearchTrees.Key key,
                                        CreativeModeTab searchTab, List<ItemStack> blueprints) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || blueprints.isEmpty()) {
            return;
        }

        CompletableFuture<SearchTree<ItemStack>> current = CreativeModeTabSearchRegistry.getNameSearchTree(key);
        if (current.isCompletedExceptionally()) {
            // Nothing usable can be joined for this key.  Build the tree the way the creative screen does;
            // it also registers the reloader that keeps it alive across a language change.  Our blueprints
            // are in the list this uses, so there is nothing to merge afterwards.
            connection.searchTrees().updateCreativeTooltips(minecraft.level.registryAccess(),
                    List.copyOf(searchTab.getDisplayItems()), key);
            installedFuture = null;
            installedBase = null;
            return;
        }

        SearchTree<ItemStack> base = current == installedFuture && installedBase != null
                ? installedBase
                : baseFrom(current, installedBase);
        installedBase = base;

        Set<ItemStack> configured = ItemStackLinkedSet.createTypeAndComponentsSet();
        configured.addAll(blueprints);

        installedFuture = CompletableFuture.completedFuture(
                new MergedSearchTree(base, deltaTree(minecraft.level.registryAccess(), blueprints), configured));
        CreativeModeTabSearchRegistry.putNameSearchTree(key, installedFuture);
    }

    /**
     * The tree to merge with, without ever waiting for it.
     *
     * <p>A build that is still running is picked up when it finishes; until then {@code fallback} - the
     * tree that was serving searches - keeps answering, so replacing the tree never blanks out the search.
     * Only the very first merge after the game was started has no fallback to offer, and there the search
     * answers with this mod's items until vanilla's build lands.</p>
     */
    private static SearchTree<ItemStack> baseFrom(CompletableFuture<SearchTree<ItemStack>> future,
                                                  SearchTree<ItemStack> fallback) {
        SearchTree<ItemStack> built = ready(future);
        if (built != null) {
            return built;
        }
        return new DeferredSearchTree(future, fallback == null ? SearchTree.empty() : fallback);
    }

    /**
     * The tree that answers while another one is still being built.
     *
     * <p>It starts out as {@code fallback} and switches to the tree of {@code pending} the moment that
     * build lands.  Building a creative search tree - a tooltip for every item in the game, and with
     * Just Enough Characters in the picture every name expanded into pinyin as well - is the most expensive
     * thing a reload can touch, and this is the only place where the wait for it is still observable, so
     * the wait is logged.</p>
     */
    private static final class DeferredSearchTree implements SearchTree<ItemStack> {
        private final CompletableFuture<SearchTree<ItemStack>> pending;
        private final SearchTree<ItemStack> fallback;
        private final long started = System.nanoTime();
        private boolean reported;

        DeferredSearchTree(CompletableFuture<SearchTree<ItemStack>> pending, SearchTree<ItemStack> fallback) {
            this.pending = pending;
            this.fallback = fallback;
        }

        @Override
        public List<ItemStack> search(String query) {
            SearchTree<ItemStack> built = ready(this.pending);
            if (built == null) {
                return this.fallback.search(query);
            }
            report();
            return built.search(query);
        }

        private void report() {
            if (this.reported) {
                return;
            }
            this.reported = true;
            // Deliberately at INFO: this is the one case a search can be incomplete for a while, and the
            // number is how long that lasted.
            CustomBuilding.LOGGER.info(
                    "The creative search tree finished building {} ms after the blueprints were merged; searches were served by the previous tree until then",
                    (System.nanoTime() - this.started) / 1_000_000L);
        }
    }

    /** The tree of a finished future, or {@code null} while it is still running or has failed. */
    private static SearchTree<ItemStack> ready(CompletableFuture<SearchTree<ItemStack>> future) {
        if (!future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }
        try {
            return future.getNow(null);
        } catch (RuntimeException exception) {
            // Cancelled between the check and the read; a search must never throw.
            return null;
        }
    }

    /** The small tree that indexes this mod's items exactly the way vanilla indexes the whole inventory. */
    private static SearchTree<ItemStack> deltaTree(HolderLookup.Provider registries, List<ItemStack> blueprints) {
        Item.TooltipContext context = Item.TooltipContext.of(registries);
        TooltipFlag flag = ClientTooltipFlag.of(TooltipFlag.Default.NORMAL.asCreative());
        return new FullTextSearchTree<>(
                stack -> tooltipLines(stack, context, flag),
                stack -> stack.getItemHolder().unwrapKey().map(ResourceKey::location).stream(),
                blueprints);
    }

    /** The items of this mod's creative tab, which are the ones the merge has to keep searchable. */
    private static List<ItemStack> blueprintsOf() {
        return List.copyOf(CustomBuilding.CUSTOM_BUILDING_TAB.get().getDisplayItems());
    }

    /** The text vanilla indexes an item under: its tooltip, with the colour codes stripped. */
    private static Stream<String> tooltipLines(ItemStack stack, Item.TooltipContext context, TooltipFlag flag) {
        return stack.getTooltipLines(context, null, flag).stream()
                .map(line -> ChatFormatting.stripFormatting(line.getString()).trim())
                .filter(line -> !line.isEmpty());
    }
}
