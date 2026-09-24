package com.plumejade.custombuilding.client.gui;

import com.plumejade.custombuilding.blueprint.BlueprintDefinition;
import com.plumejade.custombuilding.blueprint.BlueprintHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

/** Opens the blueprint panel when a configured blueprint is right-clicked. */
public final class ClientBlueprintPanel {
    private ClientBlueprintPanel() {}

    public static InteractionResult open(UseOnContext context) {
        BlueprintDefinition definition = BlueprintHelper.definitionOf(context.getItemInHand());
        if (definition == null) {
            return InteractionResult.PASS;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return InteractionResult.PASS;
        }
        minecraft.setScreen(new BlueprintScreen(definition, context.getClickedPos(), context.getClickedFace(), context.getHand()));
        return InteractionResult.SUCCESS;
    }
}
