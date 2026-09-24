package com.plumejade.custombuilding.component;

import com.plumejade.custombuilding.CustomBuilding;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data component types used by Custom Building. */
public final class CBDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CustomBuilding.MODID);

    /**
     * Stores the id of the blueprint definition (the datapack file id) an item stack was created from.
     * Two stacks of {@code custom_building:blueprint} with different values here are completely different
     * blueprints.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> BLUEPRINT =
            DATA_COMPONENTS.registerComponentType("blueprint", builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    private CBDataComponents() {}
}
