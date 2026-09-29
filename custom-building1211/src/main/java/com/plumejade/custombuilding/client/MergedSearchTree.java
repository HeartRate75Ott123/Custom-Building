package com.plumejade.custombuilding.client;

import java.util.List;
import java.util.Set;

import com.plumejade.custombuilding.CustomBuilding;

import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

/**
 * A creative search tree that answers from two trees instead of one.
 *
 * <p>Rebuilding the creative search tree means asking every item in the game for its full tooltip and
 * sorting all of those strings, which in a large modpack - and much more so with mods that expand every
 * item name into pinyin - is the most expensive thing a datapack reload can trigger.  A blueprint reload
 * only ever adds or changes a handful of stacks, so the tree that is already there is kept and searched
 * together with a tiny tree that holds this mod's items.</p>
 *
 * <p>The two trees can easily know the same stack, so the results are merged through
 * {@link ItemStackLinkedSet}, which compares stacks by item and components rather than by identity.
 * {@code configuredBlueprints} drops what a datapack removed again: only this mod's items are re-indexed
 * after a reload, so only they can go stale.</p>
 */
public final class MergedSearchTree implements SearchTree<ItemStack> {
    private final SearchTree<ItemStack> existing;
    private final SearchTree<ItemStack> blueprints;
    private final Set<ItemStack> configuredBlueprints;

    public MergedSearchTree(SearchTree<ItemStack> existing, SearchTree<ItemStack> blueprints,
                            Set<ItemStack> configuredBlueprints) {
        this.existing = existing;
        this.blueprints = blueprints;
        this.configuredBlueprints = configuredBlueprints;
    }

    @Override
    public List<ItemStack> search(String query) {
        Set<ItemStack> matches = ItemStackLinkedSet.createTypeAndComponentsSet();
        matches.addAll(this.existing.search(query));
        matches.addAll(this.blueprints.search(query));
        matches.removeIf(stack -> stack.is(CustomBuilding.BLUEPRINT.get())
                && !this.configuredBlueprints.contains(stack));
        return List.copyOf(matches);
    }
}
