package com.plumejade.custombuilding.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * The "hover the blueprint to see the building" tooltip element.
 *
 * @param texture the preview image configured by the blueprint; it is always drawn stretched into a 4:3 box
 */
public record BlueprintPreviewTooltip(ResourceLocation texture) implements TooltipComponent {
}
