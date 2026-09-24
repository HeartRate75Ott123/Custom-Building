package com.plumejade.custombuilding.blueprint;

import org.jetbrains.annotations.Nullable;

import com.plumejade.custombuilding.CustomBuilding;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Small helpers to turn blueprint definitions into item stacks and back.
 *
 * <p>1.20.1 has no data components, so a blueprint's identity lives in the stack's NBT.  The display name is
 * stored normally, with an explicit non italic style because vanilla renders custom names in italic.  The
 * tooltip lines are added by {@code BlueprintItem#appendHoverText} instead of {@code display.Lore}, because
 * vanilla wraps every lore line in square brackets in this version.</p>
 */
public final class BlueprintHelper {
    /** NBT key holding the blueprint id of a bound stack. */
    public static final String TAG_BLUEPRINT = "Blueprint";

    private BlueprintHelper() {}

    /**
     * The default example item that is always present in the creative tab.  It carries no blueprint id, so it
     * does nothing when used - exactly like the datapack documentation says.
     */
    public static ItemStack createExampleStack() {
        return new ItemStack(CustomBuilding.BLUEPRINT.get());
    }

    /** Builds the item stack of a configured blueprint. */
    public static ItemStack createStack(BlueprintDefinition definition) {
        ItemStack stack = new ItemStack(CustomBuilding.BLUEPRINT.get());
        stack.getOrCreateTag().putString(TAG_BLUEPRINT, definition.id().toString());
        stack.setHoverName(BlueprintDefinition.textComponent(definition.name())
                .withStyle(style -> style.withItalic(false)));
        return stack;
    }

    /** The blueprint a stack belongs to, or {@code null} for the plain example item. */
    @Nullable
    public static BlueprintDefinition definitionOf(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(CustomBuilding.BLUEPRINT.get())) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BLUEPRINT, Tag.TAG_STRING)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_BLUEPRINT));
        return id == null ? null : BlueprintDefinitions.byId(id);
    }

    /** The texture the item renderer has to draw for this stack. */
    public static ResourceLocation textureOf(ItemStack stack) {
        BlueprintDefinition definition = definitionOf(stack);
        return definition == null ? BlueprintPaths.DEFAULT_TEXTURE : definition.texture();
    }
}
