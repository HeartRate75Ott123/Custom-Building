package com.plumejade.custombuilding.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.plumejade.custombuilding.CustomBuilding;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Port of MC-Prefab's {@code CustomButton}: the blue Prefab button texture used for the Build and Preview
 * buttons.
 */
public class CustomButton extends ExtendedButton {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CustomBuilding.MODID, "textures/gui/prefab_button.png");
    private static final ResourceLocation TEXTURE_HOVER =
            ResourceLocation.fromNamespaceAndPath(CustomBuilding.MODID, "textures/gui/prefab_button_highlight.png");
    private static final ResourceLocation TEXTURE_PRESSED =
            ResourceLocation.fromNamespaceAndPath(CustomBuilding.MODID, "textures/gui/prefab_button_pressed.png");

    public CustomButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        this.isHovered = mouseX >= this.getX() && mouseY >= this.getY()
                && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;

        ResourceLocation texture = this.isHovered ? TEXTURE_HOVER : TEXTURE;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);

        GuiUtils.bindAndDrawScaledTexture(texture, guiGraphics, this.getX(), this.getY(),
                this.width, this.height, 90, 20, 90, 20);
        guiGraphics.drawCenteredString(minecraft.font, this.getMessage(),
                this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, 0xE0E0E0);
        guiGraphics.flush();
    }
}
