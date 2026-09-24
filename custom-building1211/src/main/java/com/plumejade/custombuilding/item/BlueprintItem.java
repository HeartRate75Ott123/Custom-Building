package com.plumejade.custombuilding.item;

import java.util.List;

import com.plumejade.custombuilding.blueprint.BlueprintHelper;
import com.plumejade.custombuilding.client.gui.ClientBlueprintPanel;
import com.plumejade.custombuilding.component.CBDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The single blueprint item.
 *
 * <p>Which building it places, what it is called, what it looks like and what it says in the tooltip are all
 * described by a datapack file and stored on the stack as data components.  An unbound stack is the default
 * example item and does nothing.</p>
 *
 * <p>Right-clicking opens the building panel on the client; the structure itself is placed by the server once
 * the player confirms the orientation, exactly like MC-Prefab does it.</p>
 */
public class BlueprintItem extends Item {
    public BlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide()) {
            // Building happens when the player presses "建造！" in the panel, see BlueprintBuildPayload.
            // Returning SUCCESS swallows the click so no block behind the panel gets used instead.
            return context.getItemInHand().get(CBDataComponents.BLUEPRINT.get()) != null
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }
        return ClientBlueprintPanel.open(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        // The configured lines are stored on the stack as DataComponents.LORE, so only the unbound example
        // item needs an extra hint here.  Adding them again would show every line twice.
        if (BlueprintHelper.definitionOf(stack) == null) {
            tooltipComponents.add(Component.translatable("tooltip.custom_building.blueprint.example")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
