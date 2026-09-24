package com.plumejade.custombuilding.item;

import java.util.List;

import javax.annotation.Nullable;

import com.plumejade.custombuilding.blueprint.BlueprintDefinition;
import com.plumejade.custombuilding.blueprint.BlueprintHelper;
import com.plumejade.custombuilding.client.gui.ClientBlueprintPanel;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The single blueprint item.
 *
 * <p>Which building it places, what it is called, what it looks like and what it says in the tooltip are all
 * described by a datapack file and stored on the stack as NBT.  An unbound stack is the default example item
 * and does nothing.</p>
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
            // Building happens when the player presses "建造！" in the panel, see BlueprintBuildPacket.
            // Returning SUCCESS swallows the click so no block behind the panel gets used instead.
            return context.getItemInHand().getTag() != null
                    && context.getItemInHand().getTag().contains(BlueprintHelper.TAG_BLUEPRINT)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }
        return ClientBlueprintPanel.open(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        BlueprintDefinition definition = BlueprintHelper.definitionOf(stack);
        if (definition == null) {
            tooltipComponents.add(Component.translatable("tooltip.custom_building.blueprint.example")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        for (String line : definition.tooltip()) {
            tooltipComponents.add(BlueprintDefinition.textComponent(line));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    /**
     * Forge has no {@code RegisterClientExtensionsEvent} in this version, so the custom item renderer is
     * attached through the item itself.  Only the client ever calls this.
     */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.plumejade.custombuilding.client.BlueprintItemRenderer.INSTANCE;
            }
        });
    }
}
