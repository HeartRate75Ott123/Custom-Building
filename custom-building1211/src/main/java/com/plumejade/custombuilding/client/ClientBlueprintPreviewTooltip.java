package com.plumejade.custombuilding.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders {@link BlueprintPreviewTooltip}.
 *
 * <p>The preview is stretched into a fixed 4:3 box.  The texture is bound directly, so the image keeps its
 * own resolution and does not have to live in an atlas.</p>
 */
public class ClientBlueprintPreviewTooltip implements ClientTooltipComponent {
    public static final int PREVIEW_WIDTH = 128;
    public static final int PREVIEW_HEIGHT = 96;

    private final ResourceLocation texture;

    public ClientBlueprintPreviewTooltip(BlueprintPreviewTooltip tooltip) {
        this.texture = tooltip.texture();
    }

    @Override
    public int getHeight() {
        return PREVIEW_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        return PREVIEW_WIDTH;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        // Using the target size as the texture size as well maps the whole image (uv 0..1) onto the box,
        // which stretches any source image into 4:3.
        guiGraphics.blit(this.texture, x, y, PREVIEW_WIDTH, PREVIEW_HEIGHT,
                0.0F, 0.0F, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }
}
