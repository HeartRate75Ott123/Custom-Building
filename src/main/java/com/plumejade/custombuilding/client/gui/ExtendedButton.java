package com.plumejade.custombuilding.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Port of MC-Prefab's {@code ExtendedButton}: a vanilla button that shortens its label with an ellipsis
 * when the text does not fit.
 */
public class ExtendedButton extends Button {
    public float fontScale = 1.0F;

    public ExtendedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
    }

    @Override
    public void renderString(GuiGraphics guiGraphics, Font font, int colour) {
        Component text = this.getMessage();
        int textWidth = font.width(text);
        int ellipsisWidth = font.width("...");

        if (textWidth > this.width - 6 && textWidth > ellipsisWidth) {
            text = Component.literal(font.substrByWidth(text, this.width - 6 - ellipsisWidth).getString() + "...");
        }

        int x = this.getX() + this.width / 2;
        int y = this.getY() + (this.height - 8) / 2;
        guiGraphics.drawCenteredString(font, text, x, y, this.getFGColor());
    }

    public int getFGColor() {
        return this.active ? 0xFFFFFF : 0xA0A0A0;
    }
}
