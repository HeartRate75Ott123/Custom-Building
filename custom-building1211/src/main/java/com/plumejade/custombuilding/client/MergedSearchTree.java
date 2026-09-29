package com.plumejade.custombuilding.client;

import java.util.ArrayList;
import java.util.List;

import com.plumejade.custombuilding.CustomBuilding;

import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.world.item.ItemStack;

/**
 * A creative search tree that answers from two trees instead of one.
 *
 * <p>Rebuilding the creative search tree means asking every item in the game for its full tooltip and
 * sorting all of those strings, which in a large modpack - and much more so with mods that expand every
 * item name into pinyin - is the most expensive thing a datapack reload can trigger.  A blueprint reload
 * only ever adds or changes a handful of stacks, so the tree that is already there is kept and searched
 * together with a tiny tree that holds this mod's blueprint items.</p>
 *
 * <p>The small tree indexes every stack of the one {@code custom_building:blueprint} item, so the big
 * tree's hits on that item are either current (and returned by the small tree already) or stale from a
 * datapack that removed the blueprint again; either way they are dropped from the big tree's results with
 * a single identity comparison.  Everything else - vanilla items, other mods' items, this mod's guide
 * book - comes out of the big tree untouched, and the merge itself never hashes a component map, which
 * keeps the per-keystroke cost identical to vanilla's.</p>
 */
public final class MergedSearchTree implements SearchTree<ItemStack> {
    /** Searches slower than this are logged with a breakdown, so the next report can say who was slow. */
    private static final long SLOW_QUERY_MS = 50L;

    private final SearchTree<ItemStack> existing;
    private final SearchTree<ItemStack> blueprints;

    public MergedSearchTree(SearchTree<ItemStack> existing, SearchTree<ItemStack> blueprints) {
        this.existing = existing;
        this.blueprints = blueprints;
    }

    @Override
    public List<ItemStack> search(String query) {
        long started = System.nanoTime();
        List<ItemStack> found = this.existing.search(query);
        long afterExisting = System.nanoTime();
        List<ItemStack> ours = this.blueprints.search(query);
        long afterOurs = System.nanoTime();

        List<ItemStack> matches = new ArrayList<>(found.size() + ours.size());
        for (ItemStack stack : found) {
            if (!stack.is(CustomBuilding.BLUEPRINT.get())) {
                matches.add(stack);
            }
        }
        matches.addAll(ours);

        long total = (System.nanoTime() - started) / 1_000_000L;
        if (total >= SLOW_QUERY_MS) {
            CustomBuilding.LOGGER.info("Slow creative search for '{}': {} ms total, {} ms in the tree vanilla built ({} results), "
                            + "{} ms in this mod's {} items, {} ms merging",
                    query, total,
                    (afterExisting - started) / 1_000_000L, found.size(),
                    (afterOurs - afterExisting) / 1_000_000L, ours.size(),
                    (System.nanoTime() - afterOurs) / 1_000_000L);
        }
        return matches;
    }
}
