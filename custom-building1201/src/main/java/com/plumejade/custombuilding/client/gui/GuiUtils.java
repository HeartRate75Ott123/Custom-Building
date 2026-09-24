package com.plumejade.custombuilding.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Texture helpers ported from MC-Prefab's {@code com.prefab.gui.GuiUtils} so that the blueprint panel
 * looks exactly like the Prefab one: the same panel textures, the same nine-slice routine and the same
 * texture scaling.
 */
public final class GuiUtils {
    private GuiUtils() {}

    /** Binds a texture for the {@code position_tex} shader. */
    public static void bindTexture(ResourceLocation texture) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, texture);
    }

    /** Draws a textured rectangle: x, y, z, width, height, textureWidth, textureHeight. */
    public static void drawTexture(ResourceLocation texture, GuiGraphics guiGraphics, int x, int y, int z,
                                   int width, int height, int textureWidth, int textureHeight) {
        guiGraphics.blit(texture, x, y, z, 0.0F, 0.0F, width, height, textureWidth, textureHeight);
    }

    /**
     * Draws a nine-slice box of any size from a fixed size textured box, filling the middle with a repeated
     * tile.  Straight port of MC-Prefab's implementation.
     */
    public static void drawContinuousTexturedBox(ResourceLocation texture, int x, int y, int u, int v,
                                                 int width, int height, int textureWidth, int textureHeight,
                                                 int topBorder, int bottomBorder, int leftBorder, int rightBorder,
                                                 float zLevel) {
        bindTexture(texture);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        int fillerWidth = textureWidth - leftBorder - rightBorder;
        int fillerHeight = textureHeight - topBorder - bottomBorder;
        int canvasWidth = width - leftBorder - rightBorder;
        int canvasHeight = height - topBorder - bottomBorder;
        int xPasses = canvasWidth / fillerWidth;
        int remainderWidth = canvasWidth % fillerWidth;
        int yPasses = canvasHeight / fillerHeight;
        int remainderHeight = canvasHeight % fillerHeight;

        // Corners.
        drawTexturedModalRect(x, y, u, v, leftBorder, topBorder, zLevel);
        drawTexturedModalRect(x + leftBorder + canvasWidth, y, u + leftBorder + fillerWidth, v, rightBorder, topBorder, zLevel);
        drawTexturedModalRect(x, y + topBorder + canvasHeight, u, v + topBorder + fillerHeight, leftBorder, bottomBorder, zLevel);
        drawTexturedModalRect(x + leftBorder + canvasWidth, y + topBorder + canvasHeight,
                u + leftBorder + fillerWidth, v + topBorder + fillerHeight, rightBorder, bottomBorder, zLevel);

        for (int i = 0; i < xPasses + (remainderWidth > 0 ? 1 : 0); i++) {
            int currentWidth = i == xPasses ? remainderWidth : fillerWidth;

            drawTexturedModalRect(x + leftBorder + (i * fillerWidth), y, u + leftBorder, v, currentWidth, topBorder, zLevel);
            drawTexturedModalRect(x + leftBorder + (i * fillerWidth), y + topBorder + canvasHeight,
                    u + leftBorder, v + topBorder + fillerHeight, currentWidth, bottomBorder, zLevel);

            for (int j = 0; j < yPasses + (remainderHeight > 0 ? 1 : 0); j++) {
                int currentHeight = j == yPasses ? remainderHeight : fillerHeight;
                drawTexturedModalRect(x + leftBorder + (i * fillerWidth), y + topBorder + (j * fillerHeight),
                        u + leftBorder, v + topBorder, currentWidth, currentHeight, zLevel);
            }
        }

        for (int j = 0; j < yPasses + (remainderHeight > 0 ? 1 : 0); j++) {
            int currentHeight = j == yPasses ? remainderHeight : fillerHeight;

            drawTexturedModalRect(x, y + topBorder + (j * fillerHeight), u, v + topBorder, leftBorder, currentHeight, zLevel);
            drawTexturedModalRect(x + leftBorder + canvasWidth, y + topBorder + (j * fillerHeight),
                    u + leftBorder + fillerWidth, v + topBorder, rightBorder, currentHeight, zLevel);
        }
    }

    /** Straight port of MC-Prefab's raw quad helper; the panel textures are sampled on a 256x256 grid. */
    public static void drawTexturedModalRect(int x, int y, int u, int v, int width, int height, float zLevel) {
        if (width <= 0 || height <= 0) {
            return;
        }
        final float uScale = 1.0F / 256.0F;
        final float vScale = 1.0F / 256.0F;

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(x, y + height, zLevel).uv(u * uScale, (v + height) * vScale).endVertex();
        builder.vertex(x + width, y + height, zLevel).uv((u + width) * uScale, (v + height) * vScale).endVertex();
        builder.vertex(x + width, y, zLevel).uv((u + width) * uScale, v * vScale).endVertex();
        builder.vertex(x, y, zLevel).uv(u * uScale, v * vScale).endVertex();

        BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
        if (rendered != null) {
            BufferUploader.drawWithShader(rendered);
        }
    }

    /** Draws a texture stretched into the given rectangle. */
    public static void bindAndDrawScaledTexture(ResourceLocation texture, GuiGraphics guiGraphics, int x, int y,
                                                int width, int height, int regionWidth, int regionHeight,
                                                int textureWidth, int textureHeight) {
        bindTexture(texture);
        guiGraphics.blit(texture, x, y, width, height, 0.0F, 0.0F, regionWidth, regionHeight, textureWidth, textureHeight);
    }
}
