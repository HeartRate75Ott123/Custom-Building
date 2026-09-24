package com.plumejade.custombuilding.blueprint;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.component.CBDataComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Small helpers to turn blueprint definitions into item stacks and back. */
public final class BlueprintHelper {
    private BlueprintHelper() {}

    /**
     * The default example item that is always present in the creative tab.  It carries no blueprint
     * component, so it does nothing when used - exactly like the datapack documentation says.
     */
    public static ItemStack createExampleStack() {
        return new ItemStack(CustomBuilding.BLUEPRINT.get());
    }

    /** Builds the item stack of a configured blueprint. */
    public static ItemStack createStack(BlueprintDefinition definition) {
        ItemStack stack = new ItemStack(CustomBuilding.BLUEPRINT.get());
        stack.set(CBDataComponents.BLUEPRINT.get(), definition.id());

        // DataComponents.ITEM_NAME instead of CUSTOM_NAME: vanilla renders CUSTOM_NAME in italic, and a
        // datapack configured name should look exactly like the author wrote it.
        stack.set(DataComponents.ITEM_NAME, BlueprintDefinition.textComponent(definition.name()));

        if (!definition.tooltip().isEmpty()) {
            // Vanilla renders lore in italic; the datapack author's text should look the way it was written,
            // and legacy § codes still override the colour as usual.
            List<Component> lines = definition.tooltip().stream()
                    .<Component>map(line -> BlueprintDefinition.textComponent(line)
                            .withStyle(style -> style.withItalic(false)))
                    .toList();
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        return stack;
    }

    /** The blueprint a stack belongs to, or {@code null} for the plain example item. */
    @Nullable
    public static BlueprintDefinition definitionOf(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(CustomBuilding.BLUEPRINT.get())) {
            return null;
        }
        ResourceLocation id = stack.get(CBDataComponents.BLUEPRINT.get());
        return id == null ? null : BlueprintDefinitions.byId(id);
    }

    /** The texture the item renderer has to draw for this stack. */
    public static ResourceLocation textureOf(ItemStack stack) {
        BlueprintDefinition definition = definitionOf(stack);
        return definition == null ? BlueprintPaths.DEFAULT_TEXTURE : definition.texture();
    }
}
